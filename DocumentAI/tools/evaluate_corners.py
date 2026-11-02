"""Measure four-corner error independently of OCR; errors normalized by image diagonal."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse,hashlib,json,math
import numpy as np
import yaml
from PIL import Image
from document_ai.reader.keypoints import KeypointLocator
p=argparse.ArgumentParser();p.add_argument('--weights',required=True);p.add_argument('--data',required=True);p.add_argument('--split',choices=['val','test'],default='test');p.add_argument('--output',required=True);a=p.parse_args()
config=yaml.safe_load(Path(a.data).read_text());root=Path(config['path']);image_dir=root/config[a.split]
images=sorted(image_dir.glob('*.png'))
if not images:raise ValueError('No exported images in selected split')
out=Path(a.output)
if out.exists():raise ValueError('Output exists')
locator=KeypointLocator(a.weights);rows=[]
for path in images:
    image=Image.open(path).convert('RGB');label=root/'labels'/a.split/(path.stem+'.txt')
    text=label.read_text().strip()
    predicted=locator(image,hashlib.sha256(path.read_bytes()).hexdigest())
    row={'image':path.name,'status':predicted['status'],'negative':not text}
    if not text:row['falsePositive']=predicted['status']=='LOCATED'
    elif predicted['status']=='LOCATED':
        values=[float(x) for x in text.split()];expected=np.asarray(values[5:]).reshape(4,3)[:,:2]*image.size
        error=np.linalg.norm(np.asarray(predicted['corners'])-expected,axis=1)/math.hypot(*image.size)
        row.update(meanNormalizedCornerError=float(error.mean()),maxNormalizedCornerError=float(error.max()))
    rows.append(row)
errors=[r['meanNormalizedCornerError'] for r in rows if 'meanNormalizedCornerError' in r]
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps({'cases':rows,'summary':{'count':len(rows),'positiveCount':sum(not r['negative'] for r in rows),'locatedPositiveCount':sum(not r['negative'] and r['status']=='LOCATED' for r in rows),'negativeCount':sum(r['negative'] for r in rows),'falsePositiveCount':sum(r.get('falsePositive',False) for r in rows),'meanNormalizedErrorOnLocatedOnly':sum(errors)/len(errors) if errors else None},'note':'Localization metrics only; misses reported separately. Evaluate OCR/QR independently. Do not tune thresholds on held-out test.'},indent=2),encoding='utf-8')
print(out)
