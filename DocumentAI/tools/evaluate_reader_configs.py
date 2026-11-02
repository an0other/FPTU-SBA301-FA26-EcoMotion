"""Compare two configurations on image cases with independent manual labels."""
import argparse
import csv
import json
import os
from pathlib import Path
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from document_ai.reader.evaluation import load_cases,evaluate,summarize


def run(args):
    cases=load_cases(args.manifest)
    for path in (args.vietocr_python,args.corner_weights,args.profile):
        if not Path(path).is_file():raise ValueError('File not found: '+str(path))
    output=Path(args.output).resolve();output.mkdir(parents=True,exist_ok=False)
    variants=[('batch-memory','full','full'),('combined','fields','adaptive')]
    variants=[(n,s,q,True) for n,s,q in variants]+([(n+'-noqr',s,q,False) for n,s,q in variants] if getattr(args,'include_no_qr',False) else [])
    all_rows=[]
    from document_ai.reader.factory import configured_reader
    for name,scope,qr,qr_enabled in variants:
        folder=output/name;folder.mkdir()
        os.environ.update(DOCUMENT_AI_RECOGNIZER='vietocr-transformer',
            DOCUMENT_AI_RECOGNIZER_DEVICE=args.device,DOCUMENT_AI_LOCALIZER_DEVICE='cpu',
            DOCUMENT_AI_CORNER_WEIGHTS=str(Path(args.corner_weights).resolve()),
            DOCUMENT_AI_FIELD_PROFILE=str(Path(args.profile).resolve()),
            DOCUMENT_AI_VIETOCR_PYTHON=str(Path(args.vietocr_python).resolve()),
            DOCUMENT_AI_PERFORMANCE_MODE='optimized',DOCUMENT_AI_BATCH_SIZE='4',
            DOCUMENT_AI_CROP_TRANSPORT='raw',DOCUMENT_AI_RECOGNITION_SCOPE=scope,
            DOCUMENT_AI_QR_SEARCH=qr,DOCUMENT_AI_QR_ENABLED='1' if qr_enabled else '0',
            DOCUMENT_AI_VIETOCR_CPU_THREADS=str(args.cpu_threads),
            DOCUMENT_AI_VIETOCR_LOG=str(folder/'vietocr-worker.log'))
        reader=None
        try:
            reader=configured_reader()
            for index,case in enumerate(cases):
                target=folder/case['id'];target.mkdir()
                print(f"[{name}] {index+1}/{len(cases)}: {case['id']}",flush=True)
                start=time.perf_counter()
                try:
                    def load(path):
                        with Path(path).open('rb') as stream:return stream.read(8388609)
                    # Expected labels/status are deliberately NOT passed to inference.
                    response=reader.read(load(case['front']),load(case['back']),case['documentType'],
                        target/'evidence' if args.save_evidence else None)
                    (target/'response.json').write_text(json.dumps(response,ensure_ascii=False,indent=2),encoding='utf-8')
                    evaluation_case=case if qr_enabled else {k:v for k,v in case.items() if k not in {'expectedStatuses','expectedQrParsed'}}
                    row=evaluate(response,evaluation_case)
                    row['checks']=response.get('checks',[])
                    row['executionReport']=response.get('executionReport',{})
                except Exception as error:
                    evaluation_case=case if qr_enabled else {k:v for k,v in case.items() if k not in {'expectedStatuses','expectedQrParsed'}}
                    row=evaluate({},evaluation_case);row['exceptionType']=type(error).__name__
                    (target/'error.json').write_text(json.dumps({'exceptionType':type(error).__name__},indent=2),encoding='utf-8')
                row.update(caseId=case['id'],variant=name,device=args.device,qrEnabled=qr_enabled,
                    expectationScope='MANIFEST' if qr_enabled else 'OCR_ONLY_DIAGNOSTIC_STATUS_AND_QR_EXPECTATIONS_NOT_APPLIED',
                    includesInitialization=index==0,wallMs=round((time.perf_counter()-start)*1000,2),
                    response=str(target.relative_to(output)/'response.json') if (target/'response.json').exists() else None)
                all_rows.append(row);write_reports(output,all_rows)
                print(f"{row['status']} {row['wallMs']:.0f} ms",flush=True)
        finally:
            if reader is not None:
                close=getattr(reader.ocr,'close',None)
                if close:close()
    print(output/'evaluation.json',flush=True)
    return 1 if any(r['status']=='PROCESSING_ERROR' for r in all_rows) else 0


def write_reports(output,rows):
    summaries={name:summarize([r for r in rows if r['variant']==name]) for name in dict.fromkeys(r['variant'] for r in rows)}
    payload={'schemaVersion':'reader-config-evaluation/0.1','summaries':summaries,'cases':rows,
        'note':'Manual labels score OCR, QR and final values separately. Missing reads and processing errors count as incorrect. Repeated photos of one card are correlated; no authenticity score. Different photos have different difficulty; first case per variant includes initialization. Old manifest profile/corners are ignored; configured learned localizer is used.'}
    (output/'evaluation.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf-8')
    columns=['variant','caseId','device','qrEnabled','status','includesInitialization','wallMs','field','expected',
        'ocrValue','qrValue','finalValue','ocrStatus','ocrExact','ocrUnaccented','qrExact','finalExact',
        'confidence','qrParsed','qrSearchCoverage','statusExpectationMet','qrExpectationMet']
    with (output/'fields.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=columns);writer.writeheader()
        for row in rows:
            for field in row['fields']:
                values={**row,**field};writer.writerow({k:values.get(k) for k in columns})


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ('manifest','output','corner-weights','vietocr-python'):p.add_argument('--'+key,required=True)
    p.add_argument('--profile',default='configs/gplx-fields.pilot.json')
    p.add_argument('--device',choices=['cpu','cuda:0'],default='cpu')
    p.add_argument('--cpu-threads',type=int,default=4);p.add_argument('--save-evidence',action='store_true')
    p.add_argument('--include-no-qr',action='store_true',help='Also repeat each configuration with QR disabled; not a QR decoder failure simulation')
    args=p.parse_args()
    if not 1<=args.cpu_threads<=32:p.error('cpu-threads must be 1..32')
    return run(args)


if __name__=='__main__':sys.exit(main())
