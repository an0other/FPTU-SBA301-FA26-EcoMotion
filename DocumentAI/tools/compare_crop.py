"""Separate processes/environments per recognizer, exact same input bytes."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse
import hashlib
import json
import subprocess
import time
import uuid
import numpy as np
from document_ai.recognition import transcription_metrics
from document_ai.core import dumps


def main():
 p=argparse.ArgumentParser()
 p.add_argument('--image',required=True);p.add_argument('--expected')
 p.add_argument('--expected-file',help='UTF-8 exact transcription; one terminal newline removed')
 p.add_argument('--paddle-python',default=sys.executable);p.add_argument('--vietocr-python')
 p.add_argument('--engines',nargs='+',default=['paddle-v5-latin','paddle-v6-tiny','paddle-v6-small'])
 p.add_argument('--repeats',type=int,default=3);p.add_argument('--timeout',type=float,default=600)
 p.add_argument('--output',default='runs/crop-benchmarks')
 a=p.parse_args()
 if a.expected is not None and a.expected_file:p.error('Choose expected or expected-file')
 expected=a.expected
 if a.expected_file:
  expected=Path(a.expected_file).read_text(encoding='utf-8-sig')
  if expected.endswith('\r\n'):expected=expected[:-2]
  elif expected.endswith('\n'):expected=expected[:-1]
 if expected=='':p.error('Expected transcription must not be empty')
 if not 1<=a.repeats<=20:p.error('repeats must be 1..20')
 if not 1<=a.timeout<=3600:p.error('timeout must be 1..3600 seconds')
 allowed={'paddle-v5-latin','paddle-v6-tiny','paddle-v6-small','vietocr-transformer','vietocr-seq2seq'}
 if len(a.engines)!=len(set(a.engines)) or any(x not in allowed for x in a.engines):p.error('Invalid or repeated engines')
 image=Path(a.image).resolve();fingerprint=hashlib.sha256(image.read_bytes()).hexdigest()
 folder=Path(a.output)/('crop-'+time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]);folder.mkdir(parents=True)
 rows=[];worker=Path(__file__).with_name('recognize_crop.py')
 for engine in a.engines:
  print(f'Recognizer: {engine}',flush=True)
  python=a.vietocr_python if engine.startswith('vietocr') else a.paddle_python
  result_path=folder/(engine+'.json')
  row={'engine':engine}
  if python is None:
   row.update(status='SKIPPED',reason='VIETOCR_INTERPRETER_NOT_CONFIGURED');rows.append(row);continue
  with (folder/(engine+'.log')).open('w',encoding='utf-8') as log:
   try:
    result=subprocess.run([python,str(worker),'--image',str(image),'--engine',engine,
      '--output',str(result_path.resolve()),'--repeats',str(a.repeats)],
      shell=False,stdout=log,stderr=subprocess.STDOUT,timeout=a.timeout)
    if not result_path.exists():
     row.update(status='ERROR',reason='WORKER_NO_RESULT',exit_code=result.returncode)
    else:
     data=json.loads(result_path.read_text(encoding='utf-8'))
     if data['input_sha256']!=fingerprint:raise ValueError('Input changed during benchmark')
     row.update(status=data['status'],exit_code=result.returncode)
     if data['status']=='COMPLETED':
      predictions=data['predictions'];warm=predictions[1:]
      row.update(text=predictions[-1]['text'],cold_ms=predictions[0]['elapsed_ms'],
       warm_p50_ms=float(np.percentile([x['elapsed_ms'] for x in warm],50)),
       warm_p95_ms=float(np.percentile([x['elapsed_ms'] for x in warm],95)),
       stable_text=len({x['text'] for x in predictions})==1,
       metrics=transcription_metrics(expected,predictions[-1]['text']) if expected is not None else None)
     else:row['error_type']=data.get('error_type')
   except subprocess.TimeoutExpired:row.update(status='ERROR',reason='WORKER_TIMEOUT')
   except OSError as exc:row.update(status='ERROR',reason='WORKER_START_ERROR',error_type=type(exc).__name__)
  rows.append(row)
  (folder/'comparison.json').write_text(dumps({'input_sha256':fingerprint,'expected':expected,'results':rows,
    'note':'Exploratory single-crop results; no automatic model selection. Confidence scales are not comparable.'}),encoding='utf-8')
 (folder/'comparison.json').write_text(dumps({'input_sha256':fingerprint,'expected':expected,'results':rows,
    'note':'Exploratory single-crop results; no automatic model selection. Confidence scales are not comparable.'}),encoding='utf-8')
 print(str(folder/'comparison.json'))

if __name__=='__main__':main()
