from document_ai.reader.field_regions import boxes,reading_order,region_extract
from PIL import Image
import json

def l(text,x,y):return dict(text=text,confidence=.95,side='FRONT',polygon=[[x,y],[x+80,y],[x+80,y+20],[x,y+20]])
def test_reading_order_groups_wrap_fragments():
 lines=[l('end',400,109),l('house',400,70),l('ward',100,110)]
 assert [v['text'] for v in reading_order(lines)]==['house','ward','end']
def test_margin_mapping():
 p={'contextMarginFraction':.06,'fields':{'x':[0,0,1,1]}}
 assert boxes(p,(1120,560))['x']==[60,30,1060,530]
def test_missing_expiry_not_invented(tmp_path):
 p=tmp_path/'p.json';p.write_text(json.dumps({'id':'pilot','documentType':'VN_DRIVING_LICENSE','contextMarginFraction':0,'fields':{'expiryDate':[0,0,1,1]}}))
 class Ocr:
  def extract(self,image):return []
 r=region_extract([], 'VN_DRIVING_LICENSE',Image.new('RGB',(300,180)),p,Ocr())
 assert r['expiryDate']['status']=='MISSING'
def test_unlimited_retry_and_conflicting_retry(tmp_path):
 p=tmp_path/'p.json';p.write_text(json.dumps({'id':'pilot','documentType':'VN_DRIVING_LICENSE','contextMarginFraction':0,'fields':{'expiryDate':[0,0,1,1]}}))
 class Ocr:
  def extract(self,image):return [l('Không thời hạn',10,10)]
 r=region_extract([], 'VN_DRIVING_LICENSE',Image.new('RGB',(300,180)),p,Ocr())
 assert r['expiryDate']['candidate']['value']=='UNLIMITED'
 r=region_extract([l('30/06/2030',10,10)],'VN_DRIVING_LICENSE',Image.new('RGB',(300,180)),p,Ocr())
 assert r['expiryDate']['status']=='AMBIGUOUS'
