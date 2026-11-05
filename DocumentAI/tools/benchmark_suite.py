"""Load each profile once; repeated warm runs plus the cold run; no fake OCR."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse
import json
import time
import numpy as np
from document_ai.core import load_profile,InputImage,dumps
from document_ai.pipeline.runner import Pipeline

p=argparse.ArgumentParser()
p.add_argument('--profiles',nargs='+',required=True)
p.add_argument('--front',required=True);p.add_argument('--back',required=True)
p.add_argument('--corners');p.add_argument('--repeats',type=int,default=3)
p.add_argument('--output',default='runs/benchmark')
a=p.parse_args()
if not 1<=a.repeats<=20:p.error('repeats must be 1..20')
folder=Path(a.output)/time.strftime('%Y%m%d-%H%M%S')
folder.mkdir(parents=True,exist_ok=False)
inputs=[]
for side,name in [('FRONT',a.front),('BACK',a.back)]:
 with Path(name).open('rb') as f:inputs.append(InputImage(side,f.read(8388609),side.lower()))
corners=json.loads(Path(a.corners).read_text(encoding='utf-8')) if a.corners else None;results=[]
for profile in a.profiles:
 print(f"PROFILE: {profile}",flush=True)
 pipeline=Pipeline(load_profile(profile),folder)
 reports=[]
 for index in range(a.repeats+1):
  print(f"  Run {index+1}/{a.repeats+1} (first includes initialization/download)",flush=True)
  report=pipeline.run(inputs,diagnostic=True,save_ocr_evidence=index==0,corners=corners)
  reports.append(report)
 warm=[r['elapsedMs'] for r in reports[1:]]
 results.append({'profile':profile,'cold_ms':reports[0]['elapsedMs'],
  'warm_p50_ms':float(np.percentile(warm,50)),'warm_p95_ms':float(np.percentile(warm,95)),
  'sample_count':len(warm),'request_ids':[r['requestId'] for r in reports],
  'assessments':[r['assessment'] for r in reports],
  'errors':[[{'check':c['check'],'side':c['image_id'],'metrics':c['metrics']} for c in r['checks'] if c['status']=='ERROR'] for r in reports]})
(folder/'benchmark.json').write_text(dumps({'results':results,'note':'Diagnostic reader benchmark, not authenticity or accuracy. p95 on few samples is exploratory.'}),encoding='utf-8')
print(str(folder/'benchmark.json'))
