import threading
from types import SimpleNamespace
import pytest
from PIL import Image
from document_ai.core import StepOutput,Status
from document_ai.reader.staged import StagedReader,reject_pair,side_hint
from document_ai.reader.fast_orientation import orient,ScaledDetector,PaddleOrientation
from document_ai.reader.request_ocr import RequestOcr,RetryBudgetExceeded
from test_v17_contract import raw,QR


def line(text,y=10):
    return {'text':text,'confidence':.99,'polygon':[[10,y],[200,y],[200,y+20],[10,y+20]],
            'recognitionEngine':'vietocr-test'}


def staged(monkeypatch,scanner=None):
    import document_ai.reader.staged as module
    texts=['GIAY PHEP LAI XE','Số/No: 790000000001','Full name: NGUYỄN AN',
           'Date of birth: 02/01/2000','Class: A1']
    class Engine:
        implementation='vietocr-test'
        calls=0
        def read_oriented(self,image,lines=None,**kwargs):
            self.calls+=1;self.last_scope={'status':'FULL'};return lines or []
    engine=Engine()
    def orientation(image,*args,**kwargs):
        front=image.getpixel((0,0))==(255,255,255)
        lines=[line(t,i*30+5) for i,t in enumerate(texts)] if front else [line('CLASSIFICATION OF MOTOR VEHICLES')]
        return {'status':'RESOLVED','rotationApplied':0,'image':image,'lines':lines,
                'attempts':[],'reason':None,'classifierError':None}
    monkeypatch.setattr(module,'orient',orientation)
    scanner=scanner or (lambda image,config,**kwargs:StepOutput(Status.PASS,payload={'values':[QR]}))
    return StagedReader(ocr=engine,locator=lambda image,h:{'status':'LOCATED','image':image},
        scanner=scanner,quality_config={'min_short_side':1,'min_laplacian_variance':0,
        'max_dark_fraction':1,'max_bright_fraction':1},performance_mode='optimized',qr_search='adaptive')


def test_mixed_qr_types_exit_before_orientation_or_body(monkeypatch):
    import document_ai.reader.staged as module
    cccd='074200000001||Nguyễn An|02012000|Nam|Test address|20072021'
    def scanner(image,config,**kwargs):
        return StepOutput(Status.PASS,payload={'values':[cccd if image.getpixel((0,0))==(255,255,255) else QR]})
    reader=staged(monkeypatch,scanner)
    monkeypatch.setattr(module,'orient',lambda *args:(_ for _ in ()).throw(AssertionError('Must exit early')))
    events=[];result=reader.read(raw('white'),raw('gray'),'AUTO',progress=events.append)
    assert result['status']=='RETAKE_REQUIRED' and result['statusReasons']==['DOCUMENT_TYPE_CONFLICT']
    assert reader.ocr.calls==0 and result['data']=={} and result['ocrData']=={}
    assert result['executionReport']['earlyExit'] is True
    assert next(c for c in result['checks'] if c['step']=='CONSISTENCY')['status']=='SKIPPED'
    assert 'READING_FIELDS' not in [e['stage'] for e in events]


def test_pair_quality_precedes_front_ocr(monkeypatch):
    import document_ai.reader.staged as module
    reader=staged(monkeypatch)
    monkeypatch.setattr(module,'quality',lambda image,c:StepOutput(Status.PASS if image.getpixel((0,0))==(255,255,255) else Status.FAIL,['BLUR']))
    result=reader.read(raw('white'),raw('gray'))
    assert result['status']=='RETAKE_REQUIRED' and reader.ocr.calls==0
    assert result['executionReport']['exitStage']=='QUALITY_CHECK'


def test_valid_pair_reads_after_precheck_and_keeps_manual_approval(monkeypatch):
    reader=staged(monkeypatch);result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert result['status']=='READ_SUCCESS' and reader.ocr.calls==2
    assert result['data']['fullName']=='Nguyễn An'
    assert result['ocrData']['fullName']['candidate']['value']=='NGUYỄN AN'
    assert result['assessment']['approval']['automaticApproval'] is False
    assert result['qrData']['searchCoverage']=='QUICK_ONLY'
    assert result['executionReport']['earlyExit'] is False


def test_no_qr_still_checks_pair_using_semantic_side_clues(monkeypatch):
    import document_ai.reader.staged as module
    reader=staged(monkeypatch);reader.qr_enabled=False
    original=module.orient
    def orientation(image,*args,**kwargs):
        result=original(image,*args,**kwargs)
        if image.getpixel((0,0))==(255,255,255):result['lines']=[line('CAN CUOC CONG DAN'),line('Full name: NGUYỄN AN')]
        return result
    monkeypatch.setattr(module,'orient',orientation)
    result=reader.read(raw('white'),raw('gray'))
    assert result['statusReasons']==['DOCUMENT_TYPE_CONFLICT'] and reader.ocr.calls==0


def test_unknown_type_is_not_rejected_as_conflicting():
    assert reject_pair([{'side':'FRONT','hint':{'documentType':None,'face':'UNKNOWN'}}],'VN_DRIVING_LICENSE')==[]
    assert side_hint([line('PERSONAL IDENTIFICATION NUMBER')])['documentType'] is None


def test_reversed_printed_sides_rejected_without_fraud_claim():
    records=[{'side':'FRONT','hint':{'documentType':'VN_DRIVING_LICENSE','face':'BACK'}}]
    assert reject_pair(records,'AUTO')==['DOCUMENT_SIDE_ORDER_CONFLICT']


def test_same_type_different_qr_numbers_rejected():
    records=[{'side':'FRONT','qr':{'payloads':[QR]}},{'side':'BACK','qr':{'payloads':[QR.replace('790000000001','790000000002')]}}]
    assert reject_pair(records,'AUTO')==['DOCUMENT_NUMBER_CONFLICT']


def test_classifier_prediction_requires_text_verification():
    classifier=SimpleNamespace(predict=lambda image:{'angle':0,'confidence':.99})
    ocr=SimpleNamespace(extract=lambda image:[line('unrelated text')])
    result=orient(Image.new('RGB',(2000,1000)),ocr,classifier)
    assert result['status']=='INCONCLUSIVE' and len(result['attempts'])<=4


def test_preview_is_bounded_and_output_coordinates_map_back():
    shapes=[]
    def extract(image):shapes.append(image.size);return [line('GIAY PHEP LAI XE')]
    classifier=SimpleNamespace(predict=lambda image:{'angle':90,'confidence':.99})
    result=orient(Image.new('RGB',(2000,1000)),SimpleNamespace(extract=extract),classifier)
    assert result['status']=='RESOLVED' and result['image'].size==(1000,2000)
    assert len(shapes)==1 and max(shapes[0])<=960
    assert result['lines'][0]['polygon'][0][0]>10


def test_classifier_failure_only_uses_small_fallback_probes():
    class Classifier:
        def predict(self,image):raise RuntimeError('unavailable')
    shapes=[]
    def extract(image):shapes.append(image.size);return []
    result=orient(Image.new('RGB',(4000,3000)),SimpleNamespace(extract=extract),Classifier())
    assert result['classifierError']=='RuntimeError' and all(max(s)<=960 for s in shapes)


def test_partial_probe_errors_do_not_establish_orientation_without_verified_model():
    count=0
    def extract(image):
        nonlocal count
        count+=1
        if count>1:raise RuntimeError('failed probe')
        return [line('GIAY PHEP LAI XE')]
    assert orient(Image.new('RGB',(800,500)),SimpleNamespace(extract=extract),None)['status']=='INCONCLUSIVE'


def test_contour_crop_context_is_sampled_from_original_pixels():
    import numpy as np
    from document_ai.reader.staged import add_crop_context
    original=Image.new('RGB',(200,100),'white');original.putpixel((50,25),(10,20,30))
    g=add_crop_context(original,{'image':original.crop((0,0,100,50)),'homography':np.eye(3).tolist()})
    assert g['image'].size==(112,56)
    assert g['image'].getpixel((56,28))==(10,20,30)
    assert g['cropContextMarginFraction']==.06


def test_scaled_detection_preserves_full_resolution_crop_coordinates():
    class Detector:
        def detect(self,image):
            assert image.size==(1280,640)
            return [{'polygon':[[128,64],[256,64],[256,128],[128,128]],'text':None}]
    result=ScaledDetector(Detector()).detect(Image.new('RGB',(4000,2000)))
    assert result[0]['polygon']==[[400.,200.],[800.,200.],[800.,400.],[400.,400.]]


def test_request_cache_reuses_but_does_not_mutate_or_cross_requests():
    class Engine:
        count=0
        def extract(self,image):self.count+=1;return [{'text':'A'}]
    engine=Engine();r=RequestOcr(engine,1);image=Image.new('RGB',(20,10))
    rows=r.extract(image);rows[0]['text']='changed'
    assert r.extract(image)[0]['text']=='A' and engine.count==1
    assert r.stats()['reusedObservationsAreIndependent'] is False
    RequestOcr(engine,1).extract(image)
    assert engine.count==2
    with pytest.raises(RetryBudgetExceeded):r.extract(Image.new('RGB',(20,10),'red'))


def test_cccd_extractor_handles_overlapping_label_and_does_not_take_signer():
    from document_ai.reader.data import extract,normalize_field
    assert normalize_field('placeOfOrigin','Mỹ')=='Mỹ'
    lines=[line('CAN CUOC CONG DAN',5),line('074200000001',35),
           line('Họ và tên / Full name',65),line('NGUYỄN AN',82),
           line('Quê quán I Place of origine',120),line('Nam Định',138)]
    for item in lines:item['side']='FRONT'
    result=extract(lines,'VN_CITIZEN_ID')
    assert result['documentNumber']['candidate']['value']=='074200000001'
    assert result['fullName']['candidate']['value']=='NGUYỄN AN'
    assert result['placeOfOrigin']['candidate']['value']!='e'


def test_classifier_adapter_checks_output_without_real_model():
    class Model:
        def predict(self,**kwargs):return [SimpleNamespace(json={'res':{'label_names':['270'],'scores':[.97]}})]
    assert PaddleOrientation(model=Model()).predict(Image.new('RGB',(10,10)))['angle']==270
