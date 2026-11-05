from types import SimpleNamespace
from pathlib import Path
import importlib.util
import pytest
from PIL import Image
from document_ai.reader.fast_orientation import orient
from document_ai.reader.vietocr_pipeline import DetectorOnlyVietOcrPipeline
from document_ai.reader.request_ocr import RequestOcr
from document_ai.adapters.ocr import crop_line
from test_v25_staged import line


def test_rectified_back_ignores_portrait_tie_seen_in_cccd_report():
    sizes=[]
    def extract(im):
        sizes.append(im.size)
        # Simulate a weak back clue incorrectly readable at the vertical quarter turn too.
        if im.getpixel((0,0))==(255,0,0) or im.width<im.height:return [line('PERSONAL IDENTIFICATION')]
        return []
    image=Image.new('RGB',(800,500),'white');image.putpixel((0,0),(255,0,0))
    result=orient(image,SimpleNamespace(extract=extract),SimpleNamespace(predict=lambda im:{'angle':0,'confidence':.92}),card_rectified=True)
    assert result['status']=='RESOLVED' and result['rotationApplied']==0
    assert all(w>h for w,h in sizes) and len(sizes)==2
    assert len([a for a in result['attempts'] if a['status']=='SKIPPED'])==2


def test_uncropped_photograph_does_not_use_landscape_card_prior():
    def extract(im):return [line('PERSONAL IDENTIFICATION')]
    result=orient(Image.new('RGB',(800,500)),SimpleNamespace(extract=extract),None,card_rectified=False)
    assert result['status']=='INCONCLUSIVE' and all(a['status']!='SKIPPED' for a in result['attempts'])


def test_strong_back_can_confirm_classifier_without_four_ocr_probes():
    calls=[]
    def extract(im):calls.append(1);return [line('ĐẶC ĐIỂM NHẬN DẠNG')]
    result=orient(Image.new('RGB',(800,500)),SimpleNamespace(extract=extract),SimpleNamespace(predict=lambda im:{'angle':0,'confidence':.92}),card_rectified=True)
    assert result['status']=='RESOLVED' and len(calls)==1


def test_rectified_landscape_opposite_directions_still_ambiguous_without_model():
    result=orient(Image.new('RGB',(800,500)),SimpleNamespace(extract=lambda im:[line('PERSONAL IDENTIFICATION')]),None,card_rectified=True)
    assert result['status']=='INCONCLUSIVE'


def pipeline():
    class Detector:
        calls=0
        def detect(self,im):
            self.calls+=1
            return [{'polygon':[[10,y],[100,y],[100,y+20],[10,y+20]],'text':None,'confidence':None} for y in (10,50)]
    class Recognizer:
        model='vgg_transformer';counts=[]
        def recognize_many(self,images):
            self.counts.append(len(images))
            return [{'text':'VALUE','confidence':.95} for _ in images]
    return DetectorOnlyVietOcrPipeline(object(),Recognizer(),Detector())


def test_focused_then_full_reuses_regions_and_reads_only_missing(monkeypatch):
    import document_ai.reader.field_scope as scope
    engine=pipeline();RequestOcr(engine)
    monkeypatch.setattr(scope,'select_field_regions',lambda regions,*a:(regions[:1],{'status':'APPLIED'}))
    im=Image.new('RGB',(200,100),'white')
    a=engine.read_oriented(im,side='FRONT');a[0]['text']='caller mutation'
    b=engine.read_oriented(im)
    assert engine.detector.calls==1 and engine.recognizer.counts==[1,1]
    assert b[0]['text']=='VALUE' and len(b)==2
    assert engine.last_timing['reusedDetection'] is True
    assert engine.last_timing['recognition']['reusedRegions']==1
    engine.end_request();RequestOcr(engine);engine.read_oriented(im)
    assert engine.detector.calls==2 and engine.recognizer.counts[-1]==2


def test_different_source_pixels_do_not_share_predictions():
    engine=pipeline();RequestOcr(engine)
    engine.read_oriented(Image.new('RGB',(200,100),'white'))
    engine.read_oriented(Image.new('RGB',(200,100),'gray'))
    assert engine.detector.calls==2 and engine.recognizer.counts==[2,2]


def test_predecoded_source_crop_matches_old_path():
    import numpy as np
    im=Image.new('RGB',(200,100),'white');im.putpixel((25,25),(1,2,3))
    poly=[[10,10],[100,12],[100,40],[10,40]]
    assert crop_line(im,poly).tobytes()==crop_line(im,poly,np.asarray(im)).tobytes()


def tool():
    path=Path(__file__).resolve().parents[1]/'tools/test_v30.py'
    spec=importlib.util.spec_from_file_location('validate_v26',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m


def test_happy_case_qr_only_early_exit_cannot_pass_validation():
    ok,reasons=tool().inspect_result({'status':'REVIEW_REQUIRED','documentType':'VN_CITIZEN_ID',
        'executionReport':{'earlyExit':True},'checks':[],'ocrData':{}},'VN_CITIZEN_ID')
    assert not ok and 'DETAILED_OCR_NOT_COMPLETED_ON_BOTH_SIDES' in reasons


def test_negative_case_does_not_need_ocr():
    assert tool().inspect_result({'status':'RETAKE_REQUIRED'},'AUTO',True)==(True,[])


def test_ready_endpoint_requires_actual_warmup():
    from fastapi.testclient import TestClient
    from document_ai.reader.api import create_app
    from test_v21_warmup import FakeReader
    with TestClient(create_app(FakeReader(),warmup_files=(),
                               api_key='test-api-key-for-document-ai-tests-0001')) as client:
        assert client.get('/health').status_code==200
        assert client.get('/ready').status_code==503


def test_staged_request_releases_cache_on_decode_error():
    from document_ai.reader.staged import StagedReader
    engine=pipeline()
    result=StagedReader(ocr=engine).read(b'not-an-image',b'also-invalid')
    assert result['status']=='RETAKE_REQUIRED' and engine._observation_cache is None
