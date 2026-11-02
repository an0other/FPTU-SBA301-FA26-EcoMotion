"""Rescore existing reports only; no OCR or QR inference."""
import argparse,json,subprocess,sys,tempfile
from pathlib import Path
p=argparse.ArgumentParser()
for k in ('manifest','evaluation','output'):p.add_argument('--'+k,required=True)
a=p.parse_args();manifest=Path(a.manifest).resolve();evaluation=Path(a.evaluation).resolve()
cases=json.loads(manifest.read_text(encoding='utf-8-sig'))['cases']
prior={c['id']:c for c in json.loads(evaluation.read_text(encoding='utf-8-sig'))['cases']}
items=[]
for c in cases:
    report=manifest.parent/c['report'] if c.get('report') else evaluation.parent/'reader-runs'/prior[c['id']]['sourceRequestId']/'report.json'
    items.append({'id':c['id'],'report':str(report.resolve()),'expected':c.get('expected',{})})
with tempfile.TemporaryDirectory() as folder:
    path=Path(folder)/'reports.json';path.write_text(json.dumps({'cases':items}),encoding='utf-8')
    result=subprocess.run([sys.executable,str(Path(__file__).with_name('evaluate_batch.py')),'--manifest',str(path),'--output',a.output])
    sys.exit(result.returncode)
