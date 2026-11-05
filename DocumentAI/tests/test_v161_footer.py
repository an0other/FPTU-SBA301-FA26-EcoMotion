import json
from PIL import Image
from document_ai.reader.footer_reading import parse_date_text,direct_footer_read
from document_ai.reader.field_regions import region_extract


def profile(tmp_path):
    d={'id':'pilot','documentType':'VN_DRIVING_LICENSE','contextMarginFraction':0,
       'fields':{'expiryDate':[0,0,1,1]},
       'directReadings':{'expiryDate':{'polygon':[[.1,.1],[.9,.1],[.9,.8],[.1,.8]],
                         'variants':['original','grayscale','clahe']}}}
    p=tmp_path/'p.json';p.write_text(json.dumps(d));return p,d


def line(text):
    return {'text':text,'confidence':.8,'recognitionEngine':'vietocr-test',
            'polygon':[[5,5],[150,5],[150,30],[5,30]]}


def test_parse_does_not_repair_missing_month_and_reads_labeled_expiry():
    assert parse_date_text('issueDate','TP. Hồ Chí Minh, ngày date 02 tháng mam/year 2023') is None
    assert parse_date_text('issueDate','ngày date 02 tháng lm onth 06 năm/year 2023')=='2023-06-02'
    assert parse_date_text('expiryDate','Có giá trị đến/Expires: 30/06/2030')=='2030-06-30'
    assert parse_date_text('expiryDate','31/02/2030') is None


def test_direct_reading_bypasses_detector_and_keeps_all_evidence(tmp_path):
    p,d=profile(tmp_path)
    class Ocr:
        def extract(self,im):return [line('Có giá trị đến/Expires: K')]
        def recognize_line_crops(self,ims):
            assert len(ims)==3
            return [line(v) for v in ['Không thời hạn','Không thời hạn','K']]
    r=region_extract([],d['documentType'],Image.new('RGB',(200,100)),p,Ocr(),tmp_path/'evidence')
    e=r['expiryDate']
    assert e['candidate']['value']=='UNLIMITED'
    assert e['candidate']['method']=='TEMPLATE_DIRECT_LINE'
    assert e['candidate']['recognitionEngine']=='vietocr-test'
    assert len(e['directReading']['attempts'])==3
    assert len(e['candidates'])==2
    assert len(list((tmp_path/'evidence').glob('expiryDate-direct-*.png')))==3


def test_direct_disagreement_not_resolved_by_majority(tmp_path):
    _,d=profile(tmp_path)
    class Ocr:
        def recognize_line_crops(self,ims):return [line(t) for t in ['Không thời hạn','Không thời hạn','30/06/2030']]
    r=direct_footer_read(Image.new('RGB',(200,100)),'expiryDate',d,Ocr())
    assert r['status']=='AMBIGUOUS' and r['candidate'] is None
    assert len(r['candidates'])==3


def test_single_valid_read_is_not_enough(tmp_path):
    _,d=profile(tmp_path)
    class Ocr:
        def recognize_line_crops(self,ims):return [line(t) for t in ['Không thời hạn','K','']]
    r=direct_footer_read(Image.new('RGB',(200,100)),'expiryDate',d,Ocr())
    assert r['status']=='INCONCLUSIVE' and r['candidate'] is None


def test_existing_valid_date_does_not_run_extra_inference(tmp_path):
    p,d=profile(tmp_path)
    class Ocr:
        def extract(self,im):return [line('30/06/2030')]
        def recognize_line_crops(self,ims):raise AssertionError('Must not run')
    r=region_extract([line('30/06/2030')],d['documentType'],Image.new('RGB',(200,100)),p,Ocr())
    assert r['expiryDate']['candidate']['value']=='2030-06-30'
    assert r['expiryDate']['directReading']['status']=='NOT_NEEDED'


def test_internal_date_conflict_preserves_candidates_and_blocks_retry(tmp_path):
    p,d=profile(tmp_path)
    class Ocr:
        def extract(self,im):return []
        def recognize_line_crops(self,ims):raise AssertionError('Must not run')
    r=region_extract([line('30/06/2030'),line('30/06/2031')],d['documentType'],Image.new('RGB',(200,100)),p,Ocr())
    assert r['expiryDate']['status']=='AMBIGUOUS'
    assert {v['value'] for v in r['expiryDate']['candidates']}=={'2030-06-30','2031-06-30'}


def test_unsupported_adapter_and_invalid_template_are_reported(tmp_path):
    _,d=profile(tmp_path)
    assert direct_footer_read(Image.new('RGB',(200,100)),'expiryDate',d,object())['status']=='NOT_SUPPORTED'
    class Ocr:
        def recognize_line_crops(self,ims):raise AssertionError('Invalid template must stop before inference')
    d['directReadings']['expiryDate']['polygon'][0]=[9,9]
    assert direct_footer_read(Image.new('RGB',(200,100)),'expiryDate',d,Ocr())['status']=='ERROR'
