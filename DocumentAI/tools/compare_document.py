import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse
import json
from document_ai.document_comparison import compare_document
from document_ai.core import dumps
p=argparse.ArgumentParser()
p.add_argument('--report',required=True);p.add_argument('--output',required=True)
p.add_argument('--name-mode',choices=['strict','unaccented'],default='unaccented')
p.add_argument('--min-confidence',type=float,default=.9)
a=p.parse_args()
r=json.loads(Path(a.report).read_text(encoding='utf-8'))
output=Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
with output.open('x',encoding='utf-8') as f:f.write(dumps(compare_document(r,a.name_mode,a.min_confidence)))
print(str(output))
