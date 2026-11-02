from types import SimpleNamespace
import hashlib,json
import numpy as np
import pytest
from PIL import Image
from document_ai.adapters.ocr import VietOcrRecognizer
from document_ai.reader.fast_orientation import orient
from document_ai.reader.localizer_recovery import RotationRecoveryLocator
from document_ai.reader.vietocr_pipeline import DetectorOnlyVietOcrPipeline
from test_v25_staged import line


def test_safe_batch_preserves_valid_results_and_isolates_nonfinite_confidence():
    class Predictor:
        def predict_batch(self,images,return_prob):return ['GOOD','BAD','ALSO GOOD'],[.95,float('nan'),.96]
        def predict(self,image,return_prob):return 'BAD',float('nan')
    r=VietOcrRecognizer();r._predictor=Predictor()
    rows=r.recognize_batch([Image.new('RGB',(30,10)) for _ in range(3)],4,tolerate_invalid=True)
    assert rows[0]['text']=='GOOD' and rows[2]['text']=='ALSO GOOD'
    assert rows[1]['status']=='ERROR' and rows[1]['text']=='' and rows[1]['confidence'] is None
    assert rows[1]['rawReturnedText']=='BAD' and r.last_single_retry_count==1
    json.dumps(rows,allow_nan=False)


def test_safe_numeric_retry_can_recover_but_does_not_replace_other_results():
    class Predictor:
        def predict_batch(self,images,return_prob):return ['BAD'],[float('nan')]
        def predict(self,image,return_prob):return 'RECOVERED',.94
    r=VietOcrRecognizer();r._predictor=Predictor()
    row=r.recognize_batch([Image.new('RGB',(30,10))],4,tolerate_invalid=True)[0]
    assert row['status']=='OK' and row['text']=='RECOVERED' and row['recoveredFrom']=='INVALID_BATCH_CONFIDENCE'


def test_numeric_recovery_budget_is_bounded():
    class Predictor:
        calls=0
        def predict_batch(self,images,return_prob):return ['BAD']*len(images),[float('nan')]*len(images)
        def predict(self,image,return_prob):self.calls+=1;return 'BAD',float('nan')
    r=VietOcrRecognizer();r._predictor=Predictor()
    rows=r.recognize_batch([Image.new('RGB',(30,10)) for _ in range(7)],4,tolerate_invalid=True,max_single_retries=2)
    assert r._predictor.calls==2 and all(row['confidence'] is None for row in rows)


def test_partial_line_failure_is_preserved_not_promoted_to_text():
    class Recognizer:
        model='vgg_transformer'
        def recognize_many(self,images):return [{'text':'','confidence':None,'status':'ERROR','errorType':'INVALID_BATCH_CONFIDENCE','rawReturnedText':'untrusted'}]
    engine=DetectorOnlyVietOcrPipeline(object(),Recognizer(),object())
    rows=engine.recognize_detected(Image.new('RGB',(220,100)),[line('ignored')])
    assert rows[0]['text']=='' and rows[0]['recognition_status']=='ERROR' and rows[0]['confidence'] is None
    assert rows[0]['rawRecognitionResult']['rawReturnedText']=='untrusted'


def test_sparse_orientation_accepts_strong_back_even_uncropped_when_model_agrees():
    calls=[]
    class Probe:
        def probe_orientation(self,im,limit):calls.append(limit);return [line('ĐẶC ĐIỂM NHẬN DẠNG')]
    result=orient(Image.new('RGB',(720,960)),Probe(),SimpleNamespace(predict=lambda im:{'angle':180,'confidence':.918}),card_rectified=False)
    assert result['status']=='RESOLVED' and result['rotationApplied']==180 and calls==[6]


def test_generic_weak_back_does_not_fast_accept_classifier():
    class Probe:
        calls=[]
        def probe_orientation(self,im,limit):self.calls.append(limit);return [line('PERSONAL IDENTIFICATION')]
    p=Probe();result=orient(Image.new('RGB',(720,960)),p,SimpleNamespace(predict=lambda im:{'angle':180,'confidence':.918}),card_rectified=False)
    assert result['status']=='INCONCLUSIVE' and len(p.calls)==4


def test_progressive_probe_escalates_when_first_lines_have_no_title():
    calls=[]
    class Probe:
        def probe_orientation(self,im,limit):
            calls.append(limit)
            return [line('unrelated')] * 6 if limit==6 else [line('CAN CUOC CONG DAN')]
    r=orient(Image.new('RGB',(900,550)),Probe(),SimpleNamespace(predict=lambda im:{'angle':0,'confidence':.95}))
    assert r['status']=='RESOLVED' and calls==[6,12]


def test_rotation_recovery_maps_homography_and_corners_back_to_original():
    class Locator:
        count=0
        def __call__(self,im,h):
            self.count+=1
            if self.count==1:return {'status':'NOT_FOUND','reason':'CARD_MASK_NOT_FOUND'}
            return {'status':'LOCATED','image':im,'corners':[[2,3],[7,3],[7,6],[2,6]],'homography':np.eye(3).tolist()}
    base=Locator();r=RotationRecoveryLocator(base)(Image.new('RGB',(10,8)),'h')
    assert base.count==2 and r['corners'][0]==[7.,4.]
    t=np.asarray(r['homography']);assert np.allclose(t@np.array([7.,4.,1.]),[2.,3.,1.])


def test_rotation_recovery_does_not_retry_ambiguous_input():
    count=[]
    def locator(im,h):count.append(1);return {'status':'AMBIGUOUS','reason':'MULTIPLE_CARD_MASKS'}
    assert RotationRecoveryLocator(locator)(Image.new('RGB',(10,8)),'h')['status']=='AMBIGUOUS' and len(count)==1


def test_cccd_back_scope_records_unread_long_bottom_lines_without_touching_front():
    from document_ai.reader.field_scope import select_field_regions
    regions=[{'polygon':[[10,y],[700,y],[700,y+20],[10,y+20]]} for y in (80,720)]
    im=Image.new('RGB',(1000,800));clues=[line('ĐẶC ĐIỂM NHẬN DẠNG')]
    kept,report=select_field_regions(regions,im,None,'BACK','VN_CITIZEN_ID',clues)
    assert kept==regions[:1] and report['mrzVerified'] is False and report['recognizedCount']==1
    kept,report=select_field_regions(regions,im,None,'FRONT','VN_CITIZEN_ID',clues)
    assert kept==regions and report['status']=='FULL'


def test_orientation_only_result_cannot_claim_partial_ocr_consistency(monkeypatch):
    import document_ai.reader.staged as module
    from test_v25_staged import staged
    from test_v17_contract import raw
    reader=staged(monkeypatch)
    monkeypatch.setattr(module,'orient',lambda image,*args,**kwargs:{'status':'INCONCLUSIVE','rotationApplied':None,'image':image,'lines':[], 'attempts':[],'reason':'ORIENTATION_NOT_RESOLVED','classifierError':None})
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    c=next(c for c in result['checks'] if c['step']=='CONSISTENCY')
    assert c['status']=='SKIPPED' and c['reasons']==['NO_OCR_FIELDS_TO_COMPARE']


def test_duplicate_masks_reduce_only_when_all_geometries_overlap_strictly(tmp_path):
    from document_ai.reader.segmentation import SegmentationLocator
    path=tmp_path/'m.pt';path.write_bytes(b'test')
    path.with_suffix('.card.json').write_text(json.dumps({'task':'card-segmentation/v1','sha256':hashlib.sha256(b'test').hexdigest()}))
    polygon=np.array([[100,100],[900,100],[900,600],[100,600]],dtype=np.float32)
    class Model:
        names={0:'document_card'}
        polygons=[polygon,polygon.copy()]
        def predict(self,**kwargs):return [SimpleNamespace(boxes=[1,2],masks=SimpleNamespace(xy=self.polygons))]
    model=Model();loc=SegmentationLocator(path,model=model)
    r=loc(Image.new('RGB',(1200,800)),'h')
    assert r['status']=='LOCATED' and r['duplicateMaskReduction']['originalCount']==2
    model.polygons=[polygon,polygon+np.array([100,0],dtype=np.float32)]
    assert loc(Image.new('RGB',(1200,800)),'h')['status']=='AMBIGUOUS'
