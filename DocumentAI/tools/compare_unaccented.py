"""Reevaluate saved crop comparison without rerunning any model."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse
import json
from document_ai.name_comparison import compare_names
from document_ai.core import dumps

p=argparse.ArgumentParser()
p.add_argument('--comparison',required=True,help='comparison.json from crop benchmark')
p.add_argument('--output',required=True)
a=p.parse_args()
d=json.loads(Path(a.comparison).read_text(encoding='utf-8'))
if not isinstance(d.get('expected'),str) or not d['expected'].strip():p.error('A nonempty reference is required')
rows=[]
for row in d['results']:
 if row.get('status')=='COMPLETED' and isinstance(row.get('text'),str):
  rows.append({'engine':row['engine'],**compare_names(d['expected'],row['text'])})
output=Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
with output.open('x',encoding='utf-8') as f:f.write(dumps({'input_sha256':d.get('input_sha256'),'results':rows,'scope':'Name text equivalence only; does not alter original benchmark or validate document.'}))
print(str(output))
