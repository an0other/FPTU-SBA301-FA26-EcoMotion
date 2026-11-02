"""One-shot fresh inference matrix; optional Rust/FP16 marked SKIPPED if unavailable."""
import argparse,json,os,subprocess,sys,time,statistics
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from document_ai.reader.experiments_v30 import PRESETS


def summarize(report):
    warm=[r for r in report.get('cases',[]) if r['phase']=='warm']
    changes=[{'case':r['case'],'phase':r['phase'],'fields':r['baselineOcrDifferences']} for r in report.get('cases',[]) if r.get('baselineOcrDifferences')]
    functional=[r['case'] for r in report.get('cases',[]) if not r.get('readingCompleted')]
    stats={}
    for name in ('gplx','cccd'):
        rows=[r for r in warm if r['case'].startswith(name+'-') and r.get('readingCompleted')]
        timings={}
        for stage in ('LOCALIZE','ORIENTATION','OCR','GEOMETRY_REUSE_FALLBACK','FIELD_EXTRACTION','QR_PRECHECK'):
            samples=[sum(t.get('durationMs',0) for t in r.get('stageTimings',[]) if t['step']==stage) for r in rows]
            timings[stage]=round(statistics.median(samples),2) if samples else None
        stats[name]={'warmMedianMs':report.get('summary',{}).get(name,{}).get('warmMedianMs'),
            'completed':len(rows),'attempted':sum(r['case'].startswith(name+'-') for r in warm),
            'stageMedianMs':timings}
    return {'documents':stats,'ocrValueChanges':changes,'functionalFailures':functional,
        'statusChanges':[{'case':r['case'],'phase':r['phase'],'changes':r['baselineStatusDifferences']} for r in report.get('cases',[]) if r.get('baselineStatusDifferences')],
        'confidenceChanges':[{'case':r['case'],'phase':r['phase'],'deltas':r['confidenceDeltas']} for r in report.get('cases',[]) if r.get('confidenceDeltas')],
        'truthFailures':[{'case':r['case'],'phase':r['phase'],'checks':r['groundTruth']} for r in report.get('cases',[]) if r.get('truthPassed') is False],
        'latencyViolations':report.get('latencyViolations',[]),
        'note':'OCR duration includes any nested rescue; do not add GEOMETRY_REUSE_FALLBACK again to get total.'}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('gplx-front','gplx-back','cccd-front','cccd-back','corner-weights','vietocr-python','output'):p.add_argument('--'+name,required=True)
    p.add_argument('--device',choices=['cpu','cuda:0'],default='cpu');p.add_argument('--repeats',type=int,default=3)
    p.add_argument('--experiments',nargs='+',choices=list(PRESETS),default=['v29','fixed','device','bucket','combined'])
    p.add_argument('--include-no-qr',action='store_true');p.add_argument('--include-rotations',action='store_true')
    p.add_argument('--truth',help='Optional manual labels JSON, not QR-derived')
    p.add_argument('--reverse-order',action='store_true')
    p.add_argument('--include-all',action='store_true');p.add_argument('--run-tests',action='store_true')
    args=p.parse_args()
    for value in (args.gplx_front,args.gplx_back,args.cccd_front,args.cccd_back,args.corner_weights,args.vietocr_python):
        if not Path(value).is_file():p.error('Missing file: '+value)
    if not Path(args.corner_weights).with_suffix('.card.json').is_file():p.error('Missing checkpoint .card.json')
    if not 2<=args.repeats<=20:p.error('Repeats must be 2..20')
    output=Path(args.output).resolve();output.mkdir(parents=True,exist_ok=False)
    if args.run_tests:
        completed=subprocess.run([sys.executable,'-m','pytest','-q'],check=False)
        if completed.returncode:return completed.returncode
    names=list(dict.fromkeys((['v29']+list(PRESETS)) if args.include_all else ['v29']+args.experiments))
    if args.reverse_order:names=['v29']+list(reversed([n for n in names if n!='v29']))
    rows=[];baseline=None
    def save():
        (output/'matrix.json').write_text(json.dumps({'schemaVersion':'documentai-experiments/0.1','experiments':rows,
            'automaticWinnerSelected':False,'note':'Sequential profiles, fresh inference, resident models per profile. Baseline is output stability, not ground truth. SKIPPED is not PASS. Compare accuracy manually, inspect confidence/status changes and hardware drift.'},ensure_ascii=False,indent=2),encoding='utf-8')
    for name in names:
        if name=='fp16' and args.device=='cpu':rows.append({'name':name,'status':'SKIPPED','reason':'FP16_REQUIRES_EXPLICIT_CUDA'});save();continue
        cmd=[sys.executable,'tools/test_v30.py','--experiment',name,'--optimization-profile','efficient','--device',args.device,'--repeats',str(args.repeats),'--output',str(output/name)]
        for key in ('gplx_front','gplx_back','cccd_front','cccd_back','corner_weights','vietocr_python'):cmd+=['--'+key.replace('_','-'),str(Path(getattr(args,key)).resolve())]
        if args.truth:cmd+=['--truth',str(Path(args.truth).resolve())]
        if baseline:cmd+=['--reference-summary',str(baseline)]
        if args.include_no_qr:cmd+=['--include-no-qr']
        if args.include_rotations:cmd+=['--include-rotations']
        print('\n=== '+name+' ===',flush=True);began=time.perf_counter()
        completed=subprocess.run(cmd,check=False)
        path=output/name/'summary.json'
        if path.is_file():
            report=json.loads(path.read_text(encoding='utf-8'))
            row={'name':name,'status':'PASS' if completed.returncode==0 else 'NEEDS_INSPECTION',
                'exitCode':completed.returncode,'suiteWallSeconds':round(time.perf_counter()-began,2),'summary':str(path),**summarize(report)}
            rows.append(row)
            if name=='v29':baseline=path
        else:rows.append({'name':name,'status':'PROCESSING_ERROR','exitCode':completed.returncode,'reason':'NO_SUMMARY_WRITTEN'})
        save()
        if name=='v29' and not baseline:
            print('Baseline not available; retained results. Stop instead of running unreferenced comparisons.');break
    print(output/'matrix.json')
    return int(any(r['status'] not in {'PASS','SKIPPED'} for r in rows))


if __name__=='__main__':raise SystemExit(main())
