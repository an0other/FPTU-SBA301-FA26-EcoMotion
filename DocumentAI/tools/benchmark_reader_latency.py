"""Repeated real inference with one resident Reader. No response/result cache."""
import argparse
import csv
import json
import os
from pathlib import Path
import statistics
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--front',required=True);p.add_argument('--back',required=True)
    p.add_argument('--corner-weights',required=True);p.add_argument('--vietocr-python',required=True)
    p.add_argument('--profile',default='configs/gplx-fields.pilot.json')
    p.add_argument('--performance-mode',choices=['standard','balanced','optimized'],default='balanced')
    p.add_argument('--batch-size',type=int,default=1);p.add_argument('--crop-transport',choices=['png','raw'],default='png')
    p.add_argument('--recognition-scope',choices=['full','fields'],default='full');p.add_argument('--qr-search',choices=['full','adaptive'],default='full')
    p.add_argument('--recognizer-device',choices=['cpu','cuda:0'],default='cpu');p.add_argument('--cpu-threads',type=int,default=4);p.add_argument('--repeats',type=int,default=3)
    p.add_argument('--document-type',default='VN_DRIVING_LICENSE');p.add_argument('--no-qr',action='store_true')
    p.add_argument('--reference-response');p.add_argument('--output',required=True)
    args=p.parse_args()
    if not 2<=args.repeats<=20:p.error('repeats must be 2..20')
    if not 1<=args.cpu_threads<=32:p.error('cpu-threads must be 1..32')
    out=Path(args.output).resolve();out.mkdir(parents=True,exist_ok=False)
    def raw(path):
        with Path(path).open('rb') as f:return f.read(8388609)
    front,back=raw(args.front),raw(args.back)
    reference=json.loads(Path(args.reference_response).read_text(encoding='utf-8-sig')) if args.reference_response else None
    os.environ.update(DOCUMENT_AI_BATCH_SIZE=str(args.batch_size),DOCUMENT_AI_CROP_TRANSPORT=args.crop_transport,DOCUMENT_AI_RECOGNITION_SCOPE=args.recognition_scope,DOCUMENT_AI_QR_SEARCH=args.qr_search,
        DOCUMENT_AI_RECOGNIZER='vietocr-transformer',DOCUMENT_AI_RECOGNIZER_DEVICE=args.recognizer_device,
        DOCUMENT_AI_CORNER_WEIGHTS=str(Path(args.corner_weights).resolve()),
        DOCUMENT_AI_VIETOCR_PYTHON=str(Path(args.vietocr_python).resolve()),
        DOCUMENT_AI_FIELD_PROFILE=str(Path(args.profile).resolve()),
        DOCUMENT_AI_PERFORMANCE_MODE=args.performance_mode,
        DOCUMENT_AI_VIETOCR_CPU_THREADS=str(args.cpu_threads),
        DOCUMENT_AI_QR_ENABLED='0' if args.no_qr else '1',
        DOCUMENT_AI_VIETOCR_LOG=str(out/'vietocr-worker.log'))
    from document_ai.reader.factory import configured_reader
    reader=configured_reader();rows=[]
    try:
        for i in range(args.repeats):
            print(f'Read {i+1}/{args.repeats}: same resident model, fresh inference',flush=True)
            start=time.perf_counter()
            result=reader.read(front,back,args.document_type)
            wall_ms=round((time.perf_counter()-start)*1000,2)
            (out/f'response-{i+1:02}.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
            baseline=reference or (rows[0]['responseData'] if rows else {'data':result['data']})
            if 'data' not in baseline:baseline={'data':baseline}
            fields=set(baseline['data'])|set(result['data'])
            changes={k:{'reference':baseline['data'].get(k),'actual':result['data'].get(k)}
                     for k in fields if baseline['data'].get(k)!=result['data'].get(k)}
            rows.append({'iteration':i+1,'firstRequest':i==0,'wallMs':wall_ms,
                         'pipelineMs':result['elapsedMs'],'status':result['status'],
                         'statusReasons':result.get('statusReasons',[]),'warnings':result.get('warnings',[]),
                         'approval':result.get('assessment',{}).get('approval'),
                         'readingAssessment':result.get('assessment',{}).get('readingAssessment'),
                         'readerConfig':result.get('readerConfig',{}),'recognitionScopeReport':result.get('recognitionScopeReport',[]),
                         'responseData':result['data'],'dataDifferences':changes,
                         'stageTimings':[{'side':c['side'],'step':c['step'],'durationMs':c['durationMs']} for c in result['checks']]})
            warm=[r['wallMs'] for r in rows[1:] if r['status']!='PROCESSING_ERROR']
            report={'schemaVersion':'reader-latency/0.1','performanceMode':args.performance_mode,
                    'cpuThreads':args.cpu_threads,'batchSize':args.batch_size,'cropTransport':args.crop_transport,'recognitionScope':args.recognition_scope,'qrSearch':args.qr_search,'recognizerDevice':args.recognizer_device,'qrEnabled':not args.no_qr,
                    'summary':{'firstRequestMs':rows[0]['wallMs'],'warmCount':len(warm),
                               'warmMedianMs':statistics.median(warm) if warm else None,
                               'warmMinMs':min(warm) if warm else None,'warmMaxMs':max(warm) if warm else None,
                               'processingErrors':sum(r['status']=='PROCESSING_ERROR' for r in rows)},
                    'runs':rows,'note':'Fresh inference each iteration, only models reused; disk evidence disabled. First request may include initialization/download. Reference comparison is output equivalence, not independent accuracy; repeated same-card inputs are correlated.'}
            (out/'benchmark.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
            print(result['status']+f' {wall_ms:.0f} ms',flush=True)
    finally:
        close=getattr(reader.ocr,'close',None)
        if close:close()
    with (out/'timings.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=['iteration','firstRequest','wallMs','pipelineMs','status'])
        writer.writeheader();writer.writerows({k:r[k] for k in writer.fieldnames} for r in rows)
    print(out/'benchmark.json')
    return 1 if any(r['status']=='PROCESSING_ERROR' for r in rows) else 0


if __name__=='__main__':sys.exit(main())
