import io
import hashlib
import numpy as np
from PIL import Image
from document_ai.geometry import rectify
from document_ai.core import InputImage,load_profile
from document_ai.pipeline.runner import Pipeline
from pathlib import Path

def data():
 image=Image.fromarray(np.random.default_rng(14).integers(30,220,(600,1000,3),dtype=np.uint8))
 stream=io.BytesIO();image.save(stream,format="PNG");raw=stream.getvalue()
 return image,raw,{'sha256':hashlib.sha256(raw).hexdigest(),'corners':[[100,100],[899,100],[899,499],[100,499]]}

def test_geometry_hash_and_transform():
 image,raw,selection=data();r=rectify(image,selection,selection['sha256'])
 assert r.status=='PASS' and r.payload['image'].size==(799,399)
 assert r.payload['mask'].shape==(600,1000)
 assert rectify(image,selection,'different').reasons==['STALE_CORNER_SELECTION']

def test_crossed_corners_rejected():
 image,_,s=data();s['corners']=[[100,100],[899,499],[899,100],[100,499]]
 assert rectify(image,s,s['sha256']).status=='FAIL'

def test_selected_geometry_flows_into_ocr_and_report(tmp_path):
 image,raw,s=data()
 class Ocr:
  def extract(self,image):
   assert image.size==(799,399)
   return [{'text':'TEST','confidence':.99,'polygon':[[1,1],[90,1],[90,30],[1,30]]}]
 p=load_profile(Path(__file__).parents[1]/'configs/reader-v5.example.json');p['required_sides']=['FRONT'];p['quality']['min_short_side']=300
 r=Pipeline(p,tmp_path,Ocr()).run([InputImage('FRONT',raw,'front')],diagnostic=True,corners={'FRONT':s})
 assert r['transforms'][0]['to']=='manual_aligned'
 assert (tmp_path/r['requestId']/'front-aligned.png').exists()
 assert next(c for c in r['checks'] if c['check']=='QUALITY')['metrics']['measurement_scope']=='card_polygon'
 assert next(c for c in r['checks'] if c['check']=='OCR')['metrics']['uncertain_line_count'] is None
