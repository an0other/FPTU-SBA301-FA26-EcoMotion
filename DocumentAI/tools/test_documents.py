"""One batch command for auto reader; labels never passed to reader inference."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse,json,csv
from document_ai.reader.service import Reader
p=argparse.ArgumentParser();p.add_argument('--manifest',required=True);p.add_argument('--output',required=True);p.add_argument('--save-evidence',action='store_true');a=p.parse_args()
manifest=Path(a.manifest).resolve();cases=json.loads(manifest.read_text(encoding='utf-8-sig'))['cases']
ids=[c['id'] for c in cases]
if len(ids)!=len(set(ids)) or not cases:raise ValueError('Unique nonempty cases required')
out=Path(a.output);out.mkdir(parents=True,exist_ok=False);reader=Reader();results=[]
for i,c in enumerate(cases):
    print(f'Case {i+1}/{len(cases)}',flush=True)
    try:
        def load(key):
            with (manifest.parent/c[key]).open('rb') as f:return f.read(8388609)
        response=reader.read(load('front'),load('back'),c.get('documentType','AUTO'),out/str(i+1)/'evidence' if a.save_evidence else None)
        folder=out/str(i+1);folder.mkdir(exist_ok=True)
        (folder/'response.json').write_text(json.dumps(response,ensure_ascii=False,indent=2),encoding='utf-8')
        results.append({'id':c['id'],'status':response['status'],'documentType':response['documentType'],'elapsedMs':response['elapsedMs'],'response':str(i+1)+'/response.json'})
    except Exception as error:results.append({'id':c['id'],'status':'PROCESSING_ERROR','exceptionType':type(error).__name__})
(out/'batch.json').write_text(json.dumps({'cases':results},ensure_ascii=False,indent=2),encoding='utf-8')
with (out/'summary.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=['id','status','documentType','elapsedMs','response','exceptionType']);writer.writeheader();writer.writerows(results)
print(out/'batch.json')
sys.exit(1 if any(r['status']=='PROCESSING_ERROR' for r in results) else 0)
