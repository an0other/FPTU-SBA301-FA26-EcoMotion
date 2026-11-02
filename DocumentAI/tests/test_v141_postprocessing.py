from PIL import Image
from document_ai.reader.orientation import resolve_orientation
from document_ai.reader.data import extract,TYPES

def line(text,x,y,w=120,h=20):
 return dict(text=text,confidence=.98,side='FRONT',polygon=[[x,y],[x+w,y],[x+w,y+h],[x,y+h]])

def test_nationality_does_not_capture_address():
 rows=[line('Quốc tịch/Nationality:',100,100,180),line('VIETNAM',400,100),line('Nơi cư trú/Address:',100,140,180),line('111/12 Q.Trung',400,140),line('P. Tăng Nhơn Phú B',100,170,220),line('Có giá trị đến/Expires:',100,210,180),line('Không thời hạn',400,210)]
 r=extract(rows,TYPES[0]);assert r['nationality']['candidate']['value']=='Việt Nam'
 assert r['address']['candidate']['value']=='111/12 Q.Trung P. Tăng Nhơn Phú B'
 assert 'thời hạn' not in r['address']['candidate']['value']

def test_rectified_landscape_disambiguates_vertical_ocr():
 class Ocr:
  count=0
  def extract(self,image):
   self.count+=1
   return [{'text':'CLASSIFICATION OF MOTOR VEHICLES'}] if self.count in (1,4) else []
 r=resolve_orientation(Image.new('RGB',(300,180)),Ocr(),card_rectified=True)
 assert r['status']=='RESOLVED' and r['rotationApplied']==0


def test_opposite_landscape_tie_stays_inconclusive():
 class Ocr:
  def extract(self,image):return [{'text':'CLASSIFICATION OF MOTOR VEHICLES'}]
 r=resolve_orientation(Image.new('RGB',(300,180)),Ocr(),card_rectified=True)
 assert r['status']=='INCONCLUSIVE'
