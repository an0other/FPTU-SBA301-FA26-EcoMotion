import json
from PIL import Image
import pytest
from document_ai.reader.read_status import resolve_read_status
from document_ai.reader.field_regions import region_extract


def status(**overrides):
    opts=dict(technical=False,failed_input=False,type_conflict=False,kind='VN_DRIVING_LICENSE',
              qr_conflict=False,mismatches=[],missing=[],uncertain=[],titles=['VN_DRIVING_LICENSE'],
              duplicate_input=False,uncropped_sides=[],unresolved_orientation=[],warnings=[])
    opts.update(overrides);return resolve_read_status(**opts)


def test_pilot_and_optional_warnings_alone_do_not_block_reading():
    assert status(warnings=['PILOT_FIELD_TEMPLATE_UNVALIDATED','OPTIONAL_FIELDS_INCOMPLETE_OR_UNCERTAIN'])==('READ_SUCCESS',[])


@pytest.mark.parametrize('options,expected,reason',[
    ({'technical':True},'PROCESSING_ERROR','STEP_EXECUTION_ERROR'),
    ({'failed_input':True},'RETAKE_REQUIRED','INPUT_OR_CARD_QUALITY_UNSUITABLE'),
    ({'missing':['fullName']},'PARTIALLY_READ','REQUIRED_FIELDS_MISSING'),
    ({'qr_conflict':True},'INCONSISTENT_DATA','QR_PAYLOAD_CONFLICT'),
    ({'uncertain':['fullName']},'REVIEW_REQUIRED','REQUIRED_FIELDS_NEED_REVIEW'),
    ({'unresolved_orientation':['BACK']},'REVIEW_REQUIRED','ORIENTATION_NOT_RESOLVED'),
    ({'titles':[]},'REVIEW_REQUIRED','TYPE_FROM_QR_ONLY'),
])
def test_real_blockers_still_report_status_reason(options,expected,reason):
    value,reasons=status(**options)
    assert value==expected and reason in reasons


def test_full_service_read_success_still_waits_for_manual_approval(tmp_path,monkeypatch):
    from test_v17_contract import fake_reader,raw
    from document_ai.reader.data import TYPES
    profile=tmp_path/'p.json'
    profile.write_text(json.dumps({'id':'pilot','documentType':TYPES[0],
                                  'validated':False,'contextMarginFraction':0,'fields':{}}))
    reader=fake_reader(monkeypatch);reader.field_profile=profile
    result=reader.read(raw('white'),raw('gray'),TYPES[0])
    assert result['status']=='READ_SUCCESS' and result['statusReasons']==[]
    assert 'PILOT_FIELD_TEMPLATE_UNVALIDATED' in result['warnings']
    assert result['assessment']['approval']['status']=='PENDING_MANUAL_REVIEW'
    assert result['assessment']['approval']['automaticApproval'] is False
    assert result['assessment']['approval']['documentVerified'] is False


def line(text):
    return {'text':text,'confidence':.95,'recognitionEngine':'vietocr-test',
            'polygon':[[5,5],[150,5],[150,30],[5,30]]}


def profile(tmp_path):
    p=tmp_path/'p.json';p.write_text(json.dumps({'id':'pilot','documentType':'VN_DRIVING_LICENSE',
      'contextMarginFraction':0,'fields':{'expiryDate':[0,0,1,1]},
      'directReadings':{'expiryDate':{'polygon':[[.1,.1],[.9,.1],[.9,.8],[.1,.8]],
                         'variants':['original','grayscale','clahe']}}}));return p


def test_direct_first_success_eliminates_roi_detector_retry(tmp_path):
    class Ocr:
        detector_calls=0
        direct_calls=0
        def extract(self,image):self.detector_calls+=1;return []
        def recognize_line_crops(self,images):
            self.direct_calls+=1;return [line('Không thời hạn') for image in images]
    ocr=Ocr();r=region_extract([], 'VN_DRIVING_LICENSE',Image.new('RGB',(200,100)),
        profile(tmp_path),ocr,retry_policy='direct_first')['expiryDate']
    assert ocr.detector_calls==0 and ocr.direct_calls==1
    assert r['candidate']['value']=='UNLIMITED'
    assert r['retryStatus']=='SKIPPED_DIRECT_READING_EXTRACTED'
    assert len(r['directReading']['attempts'])==3


def test_inconclusive_direct_falls_back_to_roi_without_repeating_direct(tmp_path):
    class Ocr:
        detector_calls=0
        direct_calls=0
        def extract(self,image):self.detector_calls+=1;return [line('30/06/2030')]
        def recognize_line_crops(self,images):
            self.direct_calls+=1;return [line('K') for image in images]
    ocr=Ocr();r=region_extract([], 'VN_DRIVING_LICENSE',Image.new('RGB',(200,100)),
        profile(tmp_path),ocr,retry_policy='direct_first')['expiryDate']
    assert ocr.detector_calls==1 and ocr.direct_calls==1
    assert r['candidate']['value']=='2030-06-30'


def test_single_direct_value_conflicting_with_roi_is_not_hidden(tmp_path):
    class Ocr:
        def extract(self,image):return [line('30/06/2030')]
        def recognize_line_crops(self,images):return [line(v) for v in ['Không thời hạn','K','K']]
    r=region_extract([], 'VN_DRIVING_LICENSE',Image.new('RGB',(200,100)),
        profile(tmp_path),Ocr(),retry_policy='direct_first')['expiryDate']
    assert r['status']=='AMBIGUOUS' and r['candidate'] is None
    assert {c['value'] for c in r['candidates']}=={'2030-06-30','UNLIMITED'}


def test_direct_valid_conflict_never_falls_back_to_vote(tmp_path):
    class Ocr:
        def extract(self,image):raise AssertionError('A valid conflict must not be overwritten')
        def recognize_line_crops(self,images):return [line(v) for v in ['Không thời hạn','Không thời hạn','30/06/2030']]
    r=region_extract([], 'VN_DRIVING_LICENSE',Image.new('RGB',(200,100)),
        profile(tmp_path),Ocr(),retry_policy='direct_first')['expiryDate']
    assert r['status']=='AMBIGUOUS' and r['candidate'] is None
