import json
from PIL import Image
from document_ai.reader.data import fuse,TYPES
from document_ai.reader.field_regions import region_extract
from document_ai.reader.policy import gplx_core_comparison


def line(text):
    return {'text':text,'confidence':.95,'polygon':[[10,10],[290,10],[290,35],[10,35]]}


def profile(tmp_path,key):
    path=tmp_path/'profile.json'
    path.write_text(json.dumps({'id':'pilot','documentType':TYPES[0],
        'contextMarginFraction':0,'fields':{key:[0,0,1,1]}}))
    return path


def test_ambiguous_address_preserves_primary_retry_and_public_candidates(tmp_path):
    class Ocr:
        def extract(self,image):return [line('111 Example Phu B')]
    result=region_extract([line('111 Example Pnu B')],TYPES[0],
        Image.new('RGB',(300,100)),profile(tmp_path,'address'),Ocr())
    entry=result['address']
    assert entry['status']=='AMBIGUOUS'
    assert entry['primaryText']=='111 Example Pnu B'
    assert entry['retryValue']=='111 Example Phu B'
    assert len(entry['candidates'])==2
    fields,issues=fuse(result,{},TYPES[0])
    assert fields['address']['value']=='111 Example Pnu B'
    assert fields['address']['status']=='OCR_BEST_EFFORT'
    assert fields['address']['ocrAmbiguous'] is True
    assert fields['address']['needsReview'] is True
    assert len(fields['address']['ocrCandidates'])==2
    assert issues==[]


def test_disagreement_on_core_name_still_blocks_candidate(tmp_path):
    class Ocr:
        def extract(self,image):return [line('TRAN BINH')]
    entry=region_extract([line('NGUYEN AN')],TYPES[0],Image.new('RGB',(300,100)),
        profile(tmp_path,'fullName'),Ocr())['fullName']
    assert entry['candidate'] is None
    assert entry['primaryText']=='NGUYEN AN'
    assert len(entry['candidates'])==2


def test_missing_primary_address_uses_retry_without_inventing_expiry(tmp_path):
    class Ocr:
        def extract(self,image):return [line('111 Example')]
    result=region_extract([],TYPES[0],Image.new('RGB',(300,100)),profile(tmp_path,'address'),Ocr())
    assert result['address']['primaryText'] is None
    assert result['address']['candidate']['attempt']=='field_retry'
    assert result['address']['candidate']['value']=='111 Example'
    fields,_=fuse(result,{'licenseClass':'A1'},TYPES[0])
    assert fields['expiryDate']['value'] is None


def test_core_scope_and_missing_and_conflict():
    qr={'documentNumber':'790000000001','fullName':'Nguyễn An','dateOfBirth':'2000-01-02','licenseClass':'A1'}
    ocr={key:{'candidate':{'value':('NGUYEN AN' if key=='fullName' else value),'confidence':.95}}
         for key,value in qr.items()}
    fields,_=fuse(ocr,qr,TYPES[0])
    assert gplx_core_comparison(fields)['status']=='MATCH'
    assert fields['address']['value'] is None
    fields['fullName']['status']='OCR_AMBIGUOUS'
    assert gplx_core_comparison(fields)['status']=='PARTIAL'
    fields['documentNumber']['status']='MISMATCH'
    assert gplx_core_comparison(fields)['status']=='MISMATCH'
    assert gplx_core_comparison({},True)['status']=='MISMATCH'


def test_cccd_address_conflict_policy_unchanged():
    ocr={'address':{'candidate':{'value':'111 Example','confidence':.95}}}
    fields,issues=fuse(ocr,{'address':'222 Example'},TYPES[1])
    assert issues==['address'] and fields['address']['status']=='MISMATCH'


def test_service_optional_address_never_blocks_core_consistency(monkeypatch):
    import io
    import document_ai.reader.service as service
    from document_ai.core import StepOutput,Status
    texts=["GIAY PHEP LAI XE/DRIVER'S LICENSE",'Số/No: 790000000001',
           'Full name: NGUYEN AN','Date of birth: 02/01/2000','Class: A1']
    lines=[{'text':v,'confidence':.99,'polygon':[]} for v in texts]
    calls=[]
    def orient(image,ocr,card_rectified):
        calls.append(1)
        return {'status':'RESOLVED','image':image,
                'lines':[dict(v) for v in lines] if len(calls)==1 else [],
                'rotationApplied':0,'attempts':[]}
    monkeypatch.setattr(service,'resolve_orientation',orient)
    monkeypatch.setattr(service,'extract',lambda ls,kind:{
        **{k:{'candidate':{'value':v,'confidence':.99},'status':'EXTRACTED'}
        for k,v in {'documentNumber':'790000000001','fullName':'NGUYEN AN',
                   'dateOfBirth':'2000-01-02','licenseClass':'A1'}.items()},
        'address':{'status':'AMBIGUOUS','candidate':{'value':'111 Example Pnu B','confidence':.8},
                   'candidates':[{'value':'111 Example Pnu B'},{'value':'111 Example Phu B'}]}})
    def image_bytes(color):
        stream=io.BytesIO();Image.new('RGB',(800,500),color).save(stream,format='PNG');return stream.getvalue()
    reader=service.Reader(ocr=object(),
        locator=lambda image,h:{'status':'LOCATED','image':image},
        scanner=lambda image,config:StepOutput(Status.PASS,payload={'values':[
            '790000000001\nNguyễn An\n02/01/2000\nA1\nTP.HCM']}),
        quality_config={'min_short_side':1,'min_laplacian_variance':0,'max_dark_fraction':1,'max_bright_fraction':1})
    result=reader.read(image_bytes('white'),image_bytes('gray'),TYPES[0])
    assert result['status']=='READ_SUCCESS'
    assert result['coreComparison']['status']=='MATCH'
    assert result['checks'][-1]['status']=='MATCH'
    assert 'address' in result['optionalReviewFields']
    assert 'expiryDate' in result['optionalMissingFields']
    assert result['missingFields']==[]
    assert result['qrMetadata']['unmappedFields'][0]['rawValue']=='TP.HCM'
    assert 'regionRaw' not in result['data']
    assert result['additionalData']['address']=='111 Example Pnu B'
    assert result['verification']['authenticity']=='NOT_VERIFIED'


def test_http_response_model_keeps_policy_and_field_flags():
    from document_ai.reader.models import ReaderResponse
    properties=ReaderResponse.model_json_schema()['properties']
    assert {'coreComparison','optionalMissingFields','optionalReviewFields'}<=properties.keys()
    from document_ai.reader.models import FieldResult
    entry=FieldResult(value='111 Example',status='OCR_BEST_EFFORT',source='OCR',
                      bestEffort=True,needsReview=True,ocrAmbiguous=True)
    assert entry.model_dump()['bestEffort'] is True
