import hashlib,io
import numpy as np
import pytest
from PIL import Image,ImageDraw
from fastapi.testclient import TestClient
from document_ai.reader.data import qr_parse,extract,fuse,TYPES
from document_ai.reader.geometry import locate
from document_ai.reader.service import Reader
from document_ai.reader.api import create_app
from document_ai.core import StepOutput,Status

CCCD='074204000111||Nguyễn An|02012000|Nam|Example address|20072021'
GPLX='790000000001\nNGUYỄN AN\n02/01/2000\nA1\nTP.HCM'


def test_cccd_empty_old_number_and_dates():
    p=qr_parse(CCCD)
    assert p['fields']['previousId'] is None
    assert p['fields']['dateOfBirth']=='2000-01-02'
    assert p['fields']['issueDate']=='2021-07-20'
    assert p['documentType']==TYPES[1]
    assert qr_parse(CCCD.replace('02012000','31022000')) is None
    assert qr_parse('https://example.com') is None


def test_gplx_qr_fields_and_no_legal_verdict():
    p=qr_parse(GPLX)
    assert p['fields']['licenseClass']=='A1'
    assert p['fields']['documentNumber']=='790000000001'


def test_name_fusion_and_mismatch_not_overwritten():
    ocr={'fullName':{'candidate':{'value':'NGUYEN AN','confidence':.99},'candidates':[]}}
    fields,issues=fuse(ocr,{'fullName':'Nguyễn An'},TYPES[1])
    assert fields['fullName']['value']=='Nguyễn An'
    assert fields['fullName']['status']=='UNACCENTED_MATCH'
    fields,issues=fuse(ocr,{'fullName':'Trần Bình'},TYPES[1])
    assert fields['fullName']['value'] is None and issues==['fullName']
    fields,_=fuse(ocr,{},TYPES[1])
    assert fields['fullName']['value']=='NGUYEN AN'


def test_single_line_label_extraction():
    lines=[{'text':t,'confidence':.99,'polygon':[[0,i*50],[500,i*50],[500,i*50+30],[0,i*50+30]]} for i,t in enumerate(['Full name: NGUYEN AN','Date of birth: 02/01/2000','Sex: Nam','Số/No: 074204000111'])]
    r=extract(lines,TYPES[1])
    assert r['documentNumber']['candidate']['value']=='074204000111'
    assert r['dateOfBirth']['candidate']['value']=='2000-01-02'
    assert r['fullName']['candidate']['value']=='NGUYEN AN'
    assert r['sex']['candidate']['value']=='MALE'


def raw_image():
    image=Image.new('RGB',(800,500),'white');stream=io.BytesIO();image.save(stream,format='PNG');return stream.getvalue()


class Ocr:
    def extract(self,image):
        texts=['CAN CUOC CONG DAN','Full name: NGUYEN AN','Date of birth: 02/01/2000','Sex: Nam','Số/No: 074204000111']
        return [{'text':t,'confidence':.99,'polygon':[]} for t in texts]


def locator(image,source_hash):return {'status':'LOCATED','image':image,'corners':[[0,0],[799,0],[799,499],[0,499]]}
def scanner(image,config):return StepOutput(Status.PASS,payload={'values':[CCCD]})


def test_service_full_fusion_and_type_conflict():
    reader=Reader(Ocr(),locator,scanner,{'min_short_side':1,'min_laplacian_variance':0,'max_dark_fraction':1,'max_bright_fraction':1})
    r=reader.read(raw_image(),raw_image(),TYPES[1])
    assert r['data']['fullName']=='Nguyễn An'
    assert r['verification']['authenticity']=='NOT_VERIFIED'
    assert r['documentType']==TYPES[1]
    conflict=reader.read(raw_image(),raw_image(),TYPES[0])
    assert conflict['status']=='REVIEW_REQUIRED'
    assert 'DOCUMENT_TYPE_CONFLICT' in conflict['reasons']


def test_bad_file_early_exit_and_api_contract():
    reader=Reader(Ocr(),locator,scanner)
    r=reader.read(b'bad',b'bad');assert r['status']=='RETAKE_REQUIRED'
    client=TestClient(create_app(reader,api_key='test-api-key-for-document-ai-tests-0001'),
                      headers={'X-API-Key':'test-api-key-for-document-ai-tests-0001'})
    response=client.post('/v1/documents/read',files={'front':('front.jpg',b'bad','image/jpeg'),'back':('back.jpg',b'bad','image/jpeg')},data={'document_type':TYPES[1]})
    assert response.status_code==200
    assert response.json()['status']=='RETAKE_REQUIRED'
    assert client.post('/v1/documents/read',files={'front':('x',b'x'),'back':('y',b'y')},data={'document_type':'UNKNOWN'}).status_code==422


def test_localizer_blank_and_card():
    assert locate(Image.new('RGB',(1000,700),'black'),'x')['status']=='NOT_FOUND'
    im=Image.new('RGB',(1000,700),'#222222');d=ImageDraw.Draw(im);d.rectangle((180,150,820,550),fill='white')
    r=locate(im,'x');assert r['status']=='LOCATED'
    assert r['semantic_type_verified'] is False


def test_uncropped_fallback_is_visible_and_never_success():
    def not_found(image,source_hash):return {'status':'NOT_FOUND','reason':'CARD_NOT_LOCATED'}
    reader=Reader(Ocr(),not_found,scanner,{'min_short_side':1,'min_laplacian_variance':0,'max_dark_fraction':1,'max_bright_fraction':1})
    r=reader.read(raw_image(),raw_image())
    assert r['status']=='REVIEW_REQUIRED'
    assert r['uncroppedSides']==['FRONT','BACK']
    assert 'UNCROPPED_IMAGE_FALLBACK' in r['warnings']
