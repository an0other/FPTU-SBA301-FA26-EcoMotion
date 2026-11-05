import io
from datetime import date
from PIL import Image
from document_ai.reader.data import TYPES,qr_parse
from document_ai.reader.assessment import assess_gplx,GPLX_MAIN_FIELDS
from document_ai.reader.service import Reader
from document_ai.core import StepOutput,Status

QR='790000000001\nNguyễn An\n02/01/2000\nA1\nTP.HCM'


def test_qr_unknown_fifth_field_never_becomes_region_or_address():
    parsed=qr_parse(QR)
    assert 'regionRaw' not in parsed['fields']
    assert 'address' not in parsed['fields']
    assert parsed['unmappedFields']==[{'position':5,'rawValue':'TP.HCM','semanticStatus':'UNCONFIRMED'}]


def fake_reader(monkeypatch,qr=QR,qr_enabled=True):
    import document_ai.reader.service as module
    calls=[]
    class Ocr:
        implementation='vietocr-test'
    texts=['GIAY PHEP LAI XE', 'Số/No: 790000000001','Full name: NGUYỄN AN',
           'Date of birth: 02/01/2000','Class: A1','Date of issue: 02/06/2023',
           'Expires: Không thời hạn','Nationality: VIỆT NAM']
    def orient(image,ocr,card_rectified):
        calls.append(1)
        return {'status':'RESOLVED','image':image,'rotationApplied':0,'attempts':[],
                'lines':[{'text':t,'confidence':.95,'polygon':[],
                          'recognitionEngine':'vietocr-vgg_transformer','primaryText':'raw paddle '+t}
                         for t in texts] if len(calls)%2==1 else []}
    monkeypatch.setattr(module,'resolve_orientation',orient)
    def scanner(image,config,**kwargs):return StepOutput(Status.PASS,payload={'values':[qr]})
    return Reader(ocr=Ocr(),locator=lambda image,h:{'status':'LOCATED','image':image},scanner=scanner,
                  qr_enabled=qr_enabled,quality_config={'min_short_side':1,'min_laplacian_variance':0,
                    'max_dark_fraction':1,'max_bright_fraction':1})


def raw(color):
    stream=io.BytesIO();Image.new('RGB',(800,500),color).save(stream,format='PNG');return stream.getvalue()


def test_model_qr_and_final_are_separate_and_traceable(monkeypatch):
    result=fake_reader(monkeypatch).read(raw('white'),raw('gray'),TYPES[0])
    assert result['schemaVersion']=='document-reader/2.0'
    assert set(result['data'])==set(GPLX_MAIN_FIELDS)
    assert 'regionRaw' not in result['fields']
    assert result['ocrData']['fullName']['candidate']['value']=='NGUYỄN AN'
    assert result['qrData']['fields']['fullName']=='Nguyễn An'
    assert result['qrData']['documents'][0]['rawPayload']==QR
    assert result['ocrLines'][0]['primaryText'].startswith('raw paddle ')
    assert result['qrMetadata']['unmappedFields'][0]['rawValue']=='TP.HCM'
    assert result['additionalData']['nationality']=='Việt Nam'
    assert result['assessment']['readingAssessment']['status']=='ALL_MAIN_FIELDS_READ'
    assert result['assessment']['expiryCheck']['status']=='NO_EXPIRY_PRINTED'
    assert result['assessment']['approval']['automaticApproval'] is False
    assert result['assessment']['imageRiskChecks']['digitalTampering']=='NOT_IMPLEMENTED'


def test_conflict_preserves_both_sources_never_overwrites_ocr(monkeypatch):
    different=QR.replace('Nguyễn An','Trần Bình')
    result=fake_reader(monkeypatch,qr=different).read(raw('white'),raw('gray'),TYPES[0])
    assert result['status']=='INCONSISTENT_DATA'
    assert result['data']['fullName'] is None
    assert result['ocrData']['fullName']['candidate']['value']=='NGUYỄN AN'
    assert result['qrData']['fields']['fullName']=='Trần Bình'
    assert result['assessment']['approval']['documentVerified'] is False


def test_no_qr_keeps_actual_model_name_and_no_fifth_field(monkeypatch):
    result=fake_reader(monkeypatch,qr_enabled=False).read(raw('white'),raw('gray'),TYPES[0])
    assert result['qrData']['status']=='DISABLED'
    assert result['qrData']['documents']==[]
    assert result['qrMetadata']['unmappedFields']==[]
    assert result['data']['fullName']=='NGUYỄN AN'
    assert result['fields']['fullName']['source']=='OCR'


def test_printed_date_checks_do_not_claim_legal_validity():
    for value,expected in [('2030-06-30','DATE_NOT_PASSED'),('2020-06-30','EXPIRED_DATE'),
                           ('2026-10-26','DATE_NOT_PASSED'),(None,'UNKNOWN'),('invalid','UNKNOWN')]:
        result=assess_gplx({'expiryDate':value},{'expiryDate':{'source':'OCR'}},{},
                          'PARTIALLY_READ',today=date(2026,10,26))
        assert result['expiryCheck']['status']==expected
        assert result['expiryCheck']['legalValidity']=='NOT_VERIFIED'
        assert result['approval']['automaticApproval'] is False


def test_http_contract_retains_all_independent_sources(monkeypatch):
    from fastapi.testclient import TestClient
    from document_ai.reader.api import create_app
    reader=fake_reader(monkeypatch)
    with TestClient(create_app(reader,api_key='test-api-key-for-document-ai-tests-0001'),
                    headers={'X-API-Key':'test-api-key-for-document-ai-tests-0001'}) as client:
        response=client.post('/v1/documents/read',files={'front':('f.png',raw('white')),
                'back':('b.png',raw('gray'))},data={'document_type':TYPES[0]})
    assert response.status_code==200
    result=response.json()
    assert result['qrData']['fields']['fullName']=='Nguyễn An'
    assert result['ocrData']['fullName']['candidate']['value']=='NGUYỄN AN'
    assert result['ocrLines'] and result['assessment']
