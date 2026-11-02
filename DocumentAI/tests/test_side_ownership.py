from document_ai.reader.data import extract,fuse,TYPES
from document_ai.reader.service import Reader
from document_ai.core import StepOutput,Status
import io
from PIL import Image


def line(text,side,x=0,y=0):
    return {'text':text,'side':side,'confidence':.99,'polygon':[[x,y],[x+300,y],[x+300,y+30],[x,y+30]]}


def test_label_never_reads_coordinates_from_other_image():
    lines=[line('Full name:','FRONT'),line('Pham Cong Nguyen','BACK',310,0)]
    r=extract(lines,TYPES[1])
    assert r['fullName']['status']=='MISSING'


def test_partial_address_does_not_claim_full_mismatch_or_match():
    o={'address':{'candidate':{'value':'111/12/10 Q.Trung','confidence':.95},'candidates':[]}}
    fields,issues=fuse(o,{'address':'111/12/10 Quang Trung, Example Ward'},TYPES[1])
    assert fields['address']['status']=='ADDRESS_NOT_CONFIRMED'
    assert fields['address']['source']=='QR' and issues==[]
    fields,issues=fuse(o,{'address':'222 Quang Trung'},TYPES[1])
    assert fields['address']['status']=='MISMATCH' and issues==['address']


def test_service_cannot_fill_holder_name_from_back_signer():
    class Ocr:
        count=0
        def extract(self,image):
            self.count+=1
            if self.count==1:return [line('CAN CUOC CONG DAN','FRONT'),line('Date of birth: 02/01/2000','FRONT')]
            return [line('Full name: Pham Cong Nguyen','BACK')]
    def locator(image,hashvalue):return {'status':'LOCATED','image':image}
    def scanner(image,config):return StepOutput(Status.PASS,payload={'values':['074200000001||Nguyễn An|02012000|Nam|111 Example street|20072021']})
    stream=io.BytesIO();Image.new('RGB',(800,500),'white').save(stream,format='PNG')
    reader=Reader(Ocr(),locator,scanner,{'min_short_side':1,'min_laplacian_variance':0,'max_dark_fraction':1,'max_bright_fraction':1})
    r=reader.read(stream.getvalue(),stream.getvalue(),TYPES[1])
    assert r['data']['fullName']=='Nguyễn An'
    assert r['fields']['fullName']['status']=='QR_ONLY'
    assert r['fields']['fullName']['ocr'] is None
    assert r['status']=='REVIEW_REQUIRED'
