import json
import math
from PIL import Image,ImageDraw
import pytest
from document_ai.reader.document_titles import title_evidence
from document_ai.reader.data import title_type,normalize_field
from document_ai.reader.quality_regions import measure_regions


@pytest.mark.parametrize('title,support,expected',[
    ("GIẤY PHÉP LÁIXE/DRIVER'S LICE",'Họ tên/Full name:','VN_DRIVING_LICENSE'),
    ('GIẤYPHÉPLÁIXE','Hạng/Class: A1','VN_DRIVING_LICENSE'),
    ('CĂNCƯỚCCÔNGDÂN','Ngày sinh/Date of birth:','VN_CITIZEN_ID'),
    ('GIẤY PHÉP LÁI X','Họ tên/Full name:',None),
    ('GIẤYPHÉPLÁIXẾP','Họ tên/Full name:',None),
    ('','Họ tên/Full name: NGUYỄN AN',None),
    ('GIẤYPHÉPLÁIXE','',None),
])
def test_spacing_tolerance_requires_complete_alias_and_independent_field_clue(title,support,expected):
    lines=[{'text':title},{'text':support}]
    assert title_type(lines)==expected
    if expected:assert title_evidence(lines)['matches'][0]['method']=='TITLE_WHITESPACE_TOLERANT_WITH_FIELD_CLUE'


def test_conflicting_titles_are_not_chosen_by_request():
    report=title_evidence([{'text':'GIẤYPHÉPLÁIXE'},{'text':'CĂNCƯỚCCÔNGDÂN'},{'text':'Họ tên/Full name:'}])
    assert report['status']=='CONFLICT' and report['documentType'] is None
    assert report['authenticityVerified'] is False


def test_number_is_not_completed_when_title_is_recovered():
    assert normalize_field('documentNumber','79000000000') is None
    assert normalize_field('documentNumber','790000000001')=='790000000001'


def test_regional_metrics_are_bounded_and_never_a_verdict():
    image=Image.new('RGB',(2000,1200),'white');draw=ImageDraw.Draw(image)
    for y in range(100,500,50):draw.text((100,y),'DOCUMENT QUALITY TEST 123',fill='black')
    report=measure_regions(image)
    assert max(report['measurementImageSize'])<=1000
    assert report['decisionUsed'] is False and report['thresholdsCalibrated'] is False
    assert report['status']=='DIAGNOSTIC_ONLY'
    assert len(report['regions'])<=13
    for region in report['regions']:
        assert all(math.isfinite(v) for v in region['metrics'].values())
        assert region['semanticRegionConfirmed'] is False


def test_quality_failed_back_still_saves_crop_and_diagnostics_without_ocr(tmp_path,monkeypatch):
    from test_v17_contract import fake_reader,raw
    reader=fake_reader(monkeypatch);reader.quality_config['min_laplacian_variance']=60
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE',tmp_path)
    assert result['status']=='RETAKE_REQUIRED'
    for side in ('front','back'):
        folder=tmp_path/side
        for name in ('original.png','card.png','localization-overlay.jpg','localization.json',
                     'quality.json','quality-regions.json','quality-regions-overlay.png'):
            assert (folder/name).is_file()
        quality=json.loads((folder/'quality.json').read_text())
        assert quality['status']=='FAIL' and 'BLUR' in quality['reasons']
    assert not (tmp_path/'back/ocr-source.png').exists()
    assert not result['ocrLines']


def test_no_regional_work_or_evidence_when_save_evidence_is_off(monkeypatch):
    from test_v17_contract import fake_reader,raw
    import document_ai.reader.quality_regions as module
    monkeypatch.setattr(module,'measure_regions',lambda image:(_ for _ in ()).throw(AssertionError('Must not run')))
    result=fake_reader(monkeypatch).read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert result['status']=='READ_SUCCESS'
    assert not any(c['step']=='QUALITY_REGIONS' for c in result['checks'])


def test_diagnostic_failure_does_not_change_quality_or_reading_status(tmp_path,monkeypatch):
    from test_v17_contract import fake_reader,raw
    import document_ai.reader.quality_regions as module
    monkeypatch.setattr(module,'measure_regions',lambda image:(_ for _ in ()).throw(RuntimeError('diagnostic')))
    result=fake_reader(monkeypatch).read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE',tmp_path)
    assert result['status']=='READ_SUCCESS'
    assert any(c['step']=='QUALITY_REGIONS' and c['status']=='INCONCLUSIVE' for c in result['checks'])
    assert (tmp_path/'back/card.png').is_file()


def test_new_title_method_is_traceable_in_response(monkeypatch):
    from test_v17_contract import fake_reader,raw
    import document_ai.reader.service as service
    reader=fake_reader(monkeypatch,qr_enabled=False);original=service.resolve_orientation
    def orientation(*args,**kwargs):
        result=original(*args,**kwargs)
        for line in result.get('lines',[]):
            if line['text']=='GIAY PHEP LAI XE':line['text']="GIẤY PHÉP LÁIXE/DRIVER'S LICE"
        return result
    monkeypatch.setattr(service,'resolve_orientation',orientation)
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert result['status']=='READ_SUCCESS'
    assert result['printedTitleEvidence']['FRONT']['matches'][0]['method']=='TITLE_WHITESPACE_TOLERANT_WITH_FIELD_CLUE'
    assert result['ocrData']['fullName']['candidate']['value']=='NGUYỄN AN'
