"""Warm-up, fresh resident-model reads and input negatives. No identity labels inferred from QR."""
import argparse,csv,io,json,os,statistics,sys,time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))


def inspect_result(result,kind,negative=False):
    if negative:return result.get('status')=='RETAKE_REQUIRED',[]
    passed={c['side'] for c in result.get('checks',[]) if c['step']=='OCR' and c['status']=='PASS' and c.get('metrics',{}).get('lineCount',0)>0}
    reasons=[]
    if passed!={'FRONT','BACK'}:reasons.append('DETAILED_OCR_NOT_COMPLETED_ON_BOTH_SIDES')
    if result.get('executionReport',{}).get('earlyExit'):reasons.append('EARLY_EXIT_ON_EXPECTED_GOOD_PAIR')
    if result.get('status') not in {'READ_SUCCESS','REVIEW_REQUIRED','PARTIALLY_READ'}:reasons.append('UNEXPECTED_READING_STATUS')
    if result.get('documentType')!=kind:reasons.append('EXPECTED_TYPE_NOT_IDENTIFIED')
    required=['documentNumber','fullName','dateOfBirth']+(['licenseClass'] if kind=='VN_DRIVING_LICENSE' else ['sex'])
    for key in required:
        field=result.get('ocrData',{}).get(key,{})
        if not field.get('candidate') or field.get('status') in {'AMBIGUOUS','MISSING'}:reasons.append('CORE_OCR_MISSING_OR_AMBIGUOUS:'+key)
    return not reasons,reasons


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ('gplx-front','gplx-back','cccd-front','cccd-back','corner-weights','vietocr-python','output'):
        p.add_argument('--'+key,required=True)
    p.add_argument('--device',choices=['cpu','cuda:0'],default='cpu')
    p.add_argument('--repeats',type=int,default=5);p.add_argument('--target-ms',type=float,default=20000)
    p.add_argument('--include-quality-cases',action='store_true');p.add_argument('--include-rotations',action='store_true');p.add_argument('--include-no-qr',action='store_true')
    p.add_argument('--focus-cccd90',action='store_true')
    p.add_argument('--truth',help='Optional independently transcribed JSON labels; never generated from QR')
    from document_ai.reader.experiments import PRESETS
    p.add_argument('--experiment',choices=list(PRESETS),default='fixed')
    p.add_argument('--optimization-profile',choices=['baseline','efficient'],default='efficient')
    p.add_argument('--reference-summary',help='Baseline summary.json; compare OCR values per matching case, not QR-filled data')
    p.add_argument('--orientation-ocr',choices=['shared','paddle'],default='shared')
    args=p.parse_args()
    truth={}
    if args.truth:
        truth=json.loads(Path(args.truth).read_text(encoding='utf-8-sig'))
        if not isinstance(truth,dict):p.error('Truth must be an object by document type')
        for kind,fields in truth.items():
            if kind not in {'VN_DRIVING_LICENSE','VN_CITIZEN_ID'} or not isinstance(fields,dict):p.error('Invalid truth schema')
            for value in fields.values():
                if value is not None and (not isinstance(value,str) or not value.strip() or any(x in value.upper() for x in ('FILL_','REPLACE_','YOUR_'))):p.error('Truth values must be actual strings or null, not placeholders')
    if not 2<=args.repeats<=20 or args.target_ms<=0:p.error('repeats must be 2..20 and target positive')
    paths=[args.gplx_front,args.gplx_back,args.cccd_front,args.cccd_back,args.corner_weights,args.vietocr_python]
    for path in paths:
        if not Path(path).is_file():p.error('File not found: '+path)
    if not Path(args.corner_weights).with_suffix('.card.json').is_file():p.error('Missing localizer .card.json sidecar')
    output=Path(args.output).resolve();output.mkdir(parents=True,exist_ok=False)
    os.environ.update(DOCUMENT_AI_PIPELINE_MODE='staged',DOCUMENT_AI_ORIENTATION_MODE='model',
        DOCUMENT_AI_ORIENTATION_OCR=args.orientation_ocr,DOCUMENT_AI_ADDRESS_RETRY='0',
        DOCUMENT_AI_CORNER_WEIGHTS=str(Path(args.corner_weights).resolve()),
        DOCUMENT_AI_VIETOCR_PYTHON=str(Path(args.vietocr_python).resolve()),
        DOCUMENT_AI_FIELD_PROFILE=str(Path('configs/gplx-fields.pilot.json').resolve()),
        DOCUMENT_AI_RECOGNIZER_DEVICE=args.device,DOCUMENT_AI_LOCALIZER_DEVICE='cpu',
        DOCUMENT_AI_RECOGNIZER='vietocr-transformer',DOCUMENT_AI_BATCH_SIZE='4',DOCUMENT_AI_CROP_TRANSPORT='raw',
        DOCUMENT_AI_PERFORMANCE_MODE='optimized',DOCUMENT_AI_RECOGNITION_SCOPE='fields',DOCUMENT_AI_QR_SEARCH='adaptive',
        DOCUMENT_AI_QR_ENABLED='1',DOCUMENT_AI_VIETOCR_LOG=str(output/'vietocr-worker.log'))
    def load(path):
        with Path(path).open('rb') as stream:return stream.read(8388609)
    gf,gb,cf,cb=[load(v) for v in paths[:4]]
    inputs=[('gplx',gf,gb,'VN_DRIVING_LICENSE'),('cccd',cf,cb,'VN_CITIZEN_ID')]
    def rotate(raw,angle):
        from PIL import Image,ImageOps
        with Image.open(io.BytesIO(raw)) as source:im=ImageOps.exif_transpose(source).convert('RGB').rotate(angle,expand=True)
        stream=io.BytesIO();im.save(stream,format='JPEG',quality=95);return stream.getvalue()
    if args.focus_cccd90:inputs=[('cccd-90',rotate(cf,90),rotate(cb,90),'VN_CITIZEN_ID')]
    rows=[];reference={};baseline={}
    if args.reference_summary:
        ref_path=Path(args.reference_summary).resolve()
        for entry in json.loads(ref_path.read_text(encoding='utf-8'))['cases']:
            if entry.get('response'):
                stored=ref_path.parent/entry['response'].replace('\\','/')
                baseline[(entry['phase'],entry['case'])]=json.loads(stored.read_text(encoding='utf-8'))
    os.environ.update(DOCUMENT_AI_READER_OPTIMIZATION=args.optimization_profile,
        DOCUMENT_AI_PREVIEW_MAX_SIDE='1280' if args.optimization_profile=='efficient' else '960',
        DOCUMENT_AI_DETECTION_MAX_SIDE='1280')
    from document_ai.reader.factory import configured_reader
    from document_ai.reader.experiments import environment
    os.environ.update(environment(args.experiment))
    reader=configured_reader()
    def run(name,front,back,kind,phase,negative=False,qr=True):
        reader.qr_enabled=qr;began=time.perf_counter()
        folder=output/phase/name;folder.mkdir(parents=True,exist_ok=True)
        print(f'[{phase}] {name}',flush=True)
        try:
            result=reader.read(front,back,kind)
            elapsed=round((time.perf_counter()-began)*1000,2)
            ok,reasons=inspect_result(result,kind,negative)
            # Compare values only, not volatile timings/polygons/confidence. This is stability, not accuracy.
            values={k:v.get('candidate',{}).get('value') if v.get('candidate') else None for k,v in result.get('ocrData',{}).items()}
            group=kind
            differences={}
            if phase=='warm' and ok:
                if group not in reference:reference[group]=values
                else:differences={k:{'first':reference[group].get(k),'current':v} for k,v in values.items() if reference[group].get(k)!=v}
            baseline_result=baseline.get((phase,name));baseline_differences={};status_differences={};confidence_deltas={}
            if baseline_result is not None and not negative:
                old={k:(v.get('candidate') or {}).get('value') for k,v in baseline_result.get('ocrData',{}).items()}
                baseline_differences={k:{'baseline':old.get(k),'current':values.get(k)} for k in set(old)|set(values) if old.get(k)!=values.get(k)}
                for key,new_field in result.get('fields',{}).items():
                    old_field=baseline_result.get('fields',{}).get(key,{})
                    old_status={k:old_field.get(k) for k in ('status','needsReview')}
                    new_status={k:new_field.get(k) for k in ('status','needsReview')}
                    if old_status!=new_status:status_differences[key]={'baseline':old_status,'current':new_status}
                    old_conf=(old_field.get('ocr') or {}).get('confidence');new_conf=(new_field.get('ocr') or {}).get('confidence')
                    if old_conf is not None and new_conf is not None and old_conf!=new_conf:confidence_deltas[key]=round(new_conf-old_conf,8)
                # Keep reading completion separate from equivalence; a changed value may be an improvement.
            equivalent=(not baseline_differences and baseline_result is not None) if args.reference_summary and not negative else None
            import unicodedata
            from document_ai.name_comparison import unaccent_name
            truth_checks=[]
            if not negative:
                for key,expected in truth.get(kind,{}).items():
                    if expected is None:continue
                    actual=values.get(key)
                    exact=actual is not None and unicodedata.normalize('NFC',actual)==unicodedata.normalize('NFC',expected)
                    truth_checks.append({'field':key,'expected':expected,'ocrValue':actual,'exactMatch':exact,
                        'unaccentedMatch':unaccent_name(actual)==unaccent_name(expected) if key=='fullName' and actual is not None else None})
            truth_ok=all(c['exactMatch'] for c in truth_checks) if truth_checks else None
            row=dict(case=name,phase=phase,status=result['status'],wallMs=elapsed,readingCompleted=ok,
                failureReasons=reasons,earlyExit=result.get('executionReport',{}).get('earlyExit'),
                exitStage=result.get('executionReport',{}).get('exitStage'),qrEnabled=qr,
                latencyTargetMet=elapsed<=args.target_ms if phase in {'warm','no-qr','rotations'} else None,
                ocrValueDifferences=differences,baselineOcrDifferences=baseline_differences,baselineValuesEquivalent=equivalent,ocrValues=values,
                baselineCoreDifferences={k:v for k,v in baseline_differences.items() if k in {'documentNumber','fullName','dateOfBirth','licenseClass','sex'}},baselineStatusDifferences=status_differences,confidenceDeltas=confidence_deltas,groundTruth=truth_checks,truthPassed=truth_ok,scopeReport=result.get('recognitionScopeReport',[]),
                stageTimings=[{'side':c['side'],'step':c['step'],'durationMs':c.get('durationMs',0)} for c in result.get('checks',[])],
                componentTimings=result.get('executionReport',{}).get('componentTimings',[]),
                optimizationCounters=result.get('executionReport',{}).get('optimizationCounters',{}),statuses={k:{'status':v.get('status'),'needsReview':v.get('needsReview'),'confidence':(v.get('ocr') or {}).get('confidence')} for k,v in result.get('fields',{}).items()},
                response=str((folder/'response.json').relative_to(output)))
            (folder/'response.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        except Exception as error:
            row=dict(case=name,phase=phase,status='PROCESSING_ERROR',wallMs=round((time.perf_counter()-began)*1000,2),
                readingCompleted=False,failureReasons=[type(error).__name__],errorMessage=str(error)[:400])
        rows.append(row);save();print(f"{row['status']} {row['wallMs']} ms; reading={row['readingCompleted']}; latency={row.get('latencyTargetMet')}; equivalent={row.get('baselineValuesEquivalent')}",flush=True)
    def save():
        stats={}
        for name,*_ in inputs:
            group=[r for r in rows if r['phase']=='warm' and r['case'].startswith(name+'-')]
            valid=[r['wallMs'] for r in group if r['readingCompleted']]
            stats[name]={'attempted':len(group),'completed':len(valid),'failed':len(group)-len(valid),
                'warmMedianMs':round(statistics.median(valid),2) if valid else None,
                'warmMaxMs':max(valid) if valid else None,'overTargetCount':sum(v>args.target_ms for v in valid)}
        report={'latencyViolations':[{'case':r['case'],'phase':r['phase'],'wallMs':r['wallMs']} for r in rows if r.get('latencyTargetMet') is False],
            'experiment':args.experiment,'experimentEnvironment':environment(args.experiment),'optimizationProfile':args.optimization_profile,'baselineComparisonRequested':bool(args.reference_summary),'schemaVersion':'reader-v31-validation/0.1','targetMs':args.target_ms,'summary':stats,'cases':rows,
            'note':'Warm times exclude explicit warm-up, include fresh full inference. Invalid/early-exit happy cases count as failures, never fast successes. Latency assertions cover warm/no-qr/rotations, not startup. Same-card stability is not independent accuracy. Startup remains separately reported.'}
        (output/'summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    try:
        for name,f,b,kind in inputs:run(name,f,b,kind,'startup')
        for iteration in range(args.repeats):
            for name,f,b,kind in inputs:run(f'{name}-{iteration+1:02}',f,b,kind,'warm')
        if not args.focus_cccd90:
            run('mixed',cf,gb,'AUTO','negative',True)
            run('duplicate',cf,cf,'AUTO','negative',True)
            run('wrong-requested-type',cf,cb,'VN_DRIVING_LICENSE','negative',True)
            if args.include_quality_cases:
                from PIL import Image
                tiny=io.BytesIO();Image.new('RGB',(40,25),'gray').save(tiny,format='PNG')
                run('empty-file',b'',gb,'VN_DRIVING_LICENSE','negative',True)
                run('tiny-image',tiny.getvalue(),gb,'VN_DRIVING_LICENSE','negative',True)
            if args.include_no_qr:
                for name,f,b,kind in inputs:run(name,f,b,kind,'no-qr',qr=False)
            if args.include_rotations:
                from PIL import Image,ImageOps
                def rotate(raw,angle):
                    with Image.open(io.BytesIO(raw)) as source:im=ImageOps.exif_transpose(source).convert('RGB').rotate(angle,expand=True)
                    stream=io.BytesIO();im.save(stream,format='JPEG',quality=95);return stream.getvalue()
                for name,f,b,kind in inputs:
                    for angle in (90,180,270):run(f'{name}-{angle}',rotate(f,angle),rotate(b,angle),kind,'rotations')
    finally:
        close=getattr(reader.ocr,'close',None)
        if close:close()
    with (output/'summary.csv').open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=['case','phase','status','wallMs','readingCompleted','earlyExit','exitStage','qrEnabled','latencyTargetMet','failureReasons','response'],extrasaction='ignore')
        writer.writeheader();writer.writerows(rows)
    print(output/'summary.json')
    return 1 if any(not r['readingCompleted'] or r.get('latencyTargetMet') is False or r.get('baselineValuesEquivalent') is False or r.get('truthPassed') is False for r in rows) else 0


if __name__=='__main__':sys.exit(main())
