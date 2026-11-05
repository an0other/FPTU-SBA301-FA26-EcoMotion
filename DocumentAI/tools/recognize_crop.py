"""Worker for ONE recognizer; never detects or recrops the supplied line."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse
import hashlib
import json
import time
from importlib.metadata import version,PackageNotFoundError
from PIL import Image,ImageOps
from document_ai.recognition import create_line_recognizer
from document_ai.core import dumps

p=argparse.ArgumentParser()
p.add_argument('--image',required=True);p.add_argument('--engine',required=True)
p.add_argument('--output',required=True);p.add_argument('--device',default='cpu')
p.add_argument('--weights');p.add_argument('--repeats',type=int,default=3)
a=p.parse_args()
if not 1<=a.repeats<=20:p.error('repeats must be 1..20')
path=Path(a.image);raw=path.read_bytes()
if len(raw)>8388608:p.error('Crop exceeds 8 MiB')
with Image.open(path) as source:
 if source.width*source.height>20_000_000:p.error('Pixel limit exceeded')
 image=ImageOps.exif_transpose(source).convert('RGB')
output=Path(a.output)
if output.exists():p.error('Choose a new output path')
report={'engine':a.engine,'input_sha256':hashlib.sha256(raw).hexdigest(),
 'width':image.width,'height':image.height,'device':a.device,'predictions':[],
 'status':'ERROR','note':'Same supplied crop, no detector, QR or spelling correction'}
try:
 recognizer=create_line_recognizer(a.engine,a.device,a.weights)
 for index in range(a.repeats+1):
  start=time.perf_counter();text,confidence=recognizer.recognize(image)
  report['predictions'].append({'text':text,'confidence':confidence,
    'elapsed_ms':round((time.perf_counter()-start)*1000,3),'initial_call':index==0})
 report['status']='COMPLETED'
except Exception as exc:
 report['error_type']=type(exc).__name__
versions={}
for name in ['paddleocr','paddlepaddle','vietocr','torch','torchvision','Pillow','numpy']:
 try:versions[name]=version(name)
 except PackageNotFoundError:versions[name]=None
report['runtimeVersions']=versions
output.parent.mkdir(parents=True,exist_ok=True)
with output.open('x',encoding='utf-8') as f:f.write(dumps(report))
print(dumps({'engine':a.engine,'status':report['status'],'output':str(output)}))
if report['status']=='ERROR':sys.exit(1)
