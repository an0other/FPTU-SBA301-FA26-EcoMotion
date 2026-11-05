import importlib.util,json
from pathlib import Path
import numpy as np
import pytest
from PIL import Image,ImageDraw
from document_ai.reader.segmentation import quad_from_polygon
from document_ai.reader.orientation import resolve_orientation

def load_tool(name):
 s=importlib.util.spec_from_file_location(name,Path(__file__).parents[1]/'tools'/(name+'.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def test_mask_start_index_does_not_change_geometry():
 p=np.array([[20,20],[330,30],[320,220],[25,210]],float);_,a=quad_from_polygon(p,400,300)
 for k in range(4):assert np.allclose(a,quad_from_polygon(np.roll(p,k,axis=0),400,300)[1])

def test_invalid_mask_rejected():
 with pytest.raises(ValueError):quad_from_polygon([[1,2],[3,4],[5,6]],400,300)

def test_orientation_uses_geometry_to_break_tie():
 class Ocr:
  def extract(self,image):
   if not(image.getpixel((0,0))==(255,0,0) or image.getpixel((0,image.height-1))==(255,0,0)):return []
   horizontal=image.width>image.height
   return [{'text':"DRIVER'S LICENSE",'confidence':.95,'polygon':[[0,0],[80,0],[80,15],[0,15]] if horizontal else [[0,0],[0,80],[15,80],[15,0]]}]
 im=Image.new('RGB',(160,100));ImageDraw.Draw(im).rectangle((0,0,10,10),fill='red')
 r=resolve_orientation(im,Ocr());assert r['status']=='RESOLVED' and r['rotationApplied']==0

def test_evaluation_measures_coordinate_error():
 m=load_tool('evaluate_localizer');p=np.array([[20,20],[330,30],[320,220],[25,210]],float)
 a=m.compare_corners(np.roll(p,2,axis=0),p,400,300);assert a['meanCornerErrorPx']==0 and a['polygonIoU']==1
 b=m.compare_corners(p+40,p,400,300);assert b['meanCornerErrorPx']>40 and b['polygonIoU']<.8

def test_dataset_conversion_keeps_split_and_geometry(tmp_path):
 m=load_tool('build_segmentation_dataset');root=tmp_path/'source';index=[]
 for split in ('train','val'):
  (root/'images'/split).mkdir(parents=True);(root/'labels'/split).mkdir(parents=True)
  Image.new('RGB',(160,100)).save(root/'images'/split/'sample.png')
  (root/'labels'/split/'sample.txt').write_text('0 .5 .5 .8 .8 .1 .1 2 .9 .1 2 .9 .9 2 .1 .9 2')
  index.append(dict(export='sample',split=split,sha256=split,group=split))
 (root/'index.json').write_text(json.dumps(index));out=tmp_path/'new';m.convert(root,out)
 assert len((out/'labels/train/sample.txt').read_text().split())==9
 assert json.loads((out/'index.json').read_text())==index

def test_new_locator_rejects_legacy_checkpoint(tmp_path):
 from document_ai.reader.localizers import load_locator
 p=tmp_path/'old.pt';p.write_bytes(b'old')
 p.with_suffix('.card.json').write_text(json.dumps({'task':'card-four-corners/v2'}))
 with pytest.raises(ValueError,match='Legacy pose'):load_locator(p)

def test_seg_locator_runs_mask_geometry(tmp_path):
 import hashlib
 from types import SimpleNamespace
 from document_ai.reader.segmentation import SegmentationLocator
 p=tmp_path/'best.pt';p.write_bytes(b'test')
 p.with_suffix('.card.json').write_text(json.dumps({'task':'card-segmentation/v1','sha256':hashlib.sha256(b'test').hexdigest()}))
 class Fake:
  names={0:'document_card'}
  def predict(self,**kwargs):
   assert kwargs['rect'] is False
   return [SimpleNamespace(boxes=[1],masks=SimpleNamespace(xy=[np.array([[20,20],[330,30],[320,220],[25,210]],float)]))]
 r=SegmentationLocator(p,model=Fake())(Image.new('RGB',(400,300)),'source')
 assert r['status']=='LOCATED' and r['maskQuadIoU']>.93
