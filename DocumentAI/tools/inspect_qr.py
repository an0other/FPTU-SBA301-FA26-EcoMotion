"""QR-only diagnostic. Raw payload stays in private output JSON."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse
from document_ai.core import dumps
from document_ai.imaging import decode
from document_ai.adapters.qr import scan_qr

p=argparse.ArgumentParser()
p.add_argument('--image',required=True)
p.add_argument('--aligned')
p.add_argument('--output',default='runs/qr-inspection.json')
p.add_argument('--roi',nargs=4,type=float)
p.add_argument('--save-evidence',action='store_true')
a=p.parse_args()
def read_image(name):
 with Path(name).open('rb') as f:raw=f.read(8388609)
 result=decode(raw,{})
 if result.status!='PASS':p.exit(2,'Image failed validation\n')
 return result.payload['image']
image=read_image(a.image);alternate=read_image(a.aligned) if a.aligned else None
output=Path(a.output)
if output.exists():p.exit(2,'Output exists; choose a fresh path\n')
r=scan_qr(image,{'roi':a.roi} if a.roi else {},alternate,a.save_evidence)
output.parent.mkdir(parents=True,exist_ok=True)
refs=[]
for name,content in r.evidence.items():
 path=output.parent/(output.stem+'-'+name)
 with path.open('xb') as f:f.write(content)
 refs.append(path.name)
with output.open('x',encoding='utf-8') as f:f.write(dumps({'status':r.status,'reasons':r.reasons,'metrics':r.metrics,'payloads':r.payload['values'],'evidence':refs}))
print(dumps({'status':r.status,'reasons':r.reasons,'report':str(output)}))
