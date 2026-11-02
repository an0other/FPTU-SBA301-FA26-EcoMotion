"""Evaluate transcriptions against manually labeled lines in ONE exact report."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse
import hashlib
import json
import unicodedata
from document_ai.core import dumps

def distance(a,b):
 row=list(range(len(b)+1))
 for i,x in enumerate(a,1):
  new=[i]
  for j,y in enumerate(b,1):new.append(min(new[-1]+1,row[j]+1,row[j-1]+(x!=y)))
  row=new
 return row[-1]
def norm(s):return unicodedata.normalize('NFC',s)
def main():
 p=argparse.ArgumentParser();p.add_argument('--report',required=True);p.add_argument('--labels',required=True)
 p.add_argument('--init',action='store_true');p.add_argument('--output');p.add_argument('--comparison')
 a=p.parse_args();raw=Path(a.report).read_bytes();r=json.loads(raw)
 digest=hashlib.sha256(raw).hexdigest();labelpath=Path(a.labels)
 if a.init:
  with labelpath.open('x',encoding='utf-8') as f:f.write(dumps({'report_sha256':digest,'lines':[{'side':side,'index':i,'expected':None} for side,lines in r['ocrLines'].items() for i,_ in enumerate(lines)]}))
  print('Created labels; replace null with exact visible transcription, keep unreadable lines null.');return
 labels=json.loads(labelpath.read_text(encoding='utf-8'))
 if labels['report_sha256']!=digest:raise ValueError('Labels belong to a different report')
 candidates={}
 if a.comparison:
  comp=json.loads(Path(a.comparison).read_text(encoding='utf-8'))
  if comp['source_request_id']!=r['requestId']:raise ValueError('Comparison belongs to another run')
  candidates={(x['side'],x['line']):x['vietocr'] for x in comp['rows']}
 scores={};seen=set();labeled=0
 for label in labels['lines']:
  key=(label['side'],label['index'])
  if key in seen:raise ValueError('Duplicate label')
  seen.add(key);truth=label['expected']
  if truth is None:continue
  if not isinstance(truth,str) or not truth:raise ValueError('Expected must be nonempty text or null')
  labeled+=1;truth=norm(truth)
  values={'paddle':r['ocrLines'][key[0]][key[1]]['text']}
  if key in candidates:values['vietocr']=candidates[key]
  for engine,text in values.items():
   item=scores.setdefault(engine,{'lines':0,'exact':0,'edits':0,'characters':0})
   item['lines']+=1;item['exact']+=norm(text)==truth
   item['edits']+=distance(norm(text),truth);item['characters']+=len(truth)
 for item in scores.values():item['cer']=item['edits']/item['characters'];item['exact_rate']=item['exact']/item['lines']
 output=Path(a.output or str(labelpath.with_suffix('.metrics.json')))
 with output.open('x',encoding='utf-8') as f:f.write(dumps({'labeled_lines':labeled,'label_entries':len(labels['lines']),'scores':scores,'normalization':'NFC only; case, accents, spaces and punctuation preserved'}))
 print(str(output))
if __name__=='__main__':main()
