"""One entry point for full reader evaluation + recovery + consolidated review."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse,csv,html,json,os,subprocess,time
from document_ai.core import dumps
from document_ai.document_comparison import compare_document


def run(args):
    start=time.perf_counter()
    manifest=Path(args.manifest).resolve()
    data=json.loads(manifest.read_text(encoding='utf-8-sig'))
    cases=data.get('cases',[])
    if not cases:raise ValueError('Manifest needs cases')
    ids=[c['id'] for c in cases]
    if len(set(ids))!=len(ids):raise ValueError('Duplicate case IDs')
    for c in cases:
        for value in c.get('expected',{}).values():
            if not isinstance(value,str) or not value.strip() or value.startswith('FILL_'):raise ValueError('Replace placeholder labels or omit labels')
    out=Path(args.output).resolve()
    if out.exists():raise ValueError('Choose a NEW output directory')
    tools=Path(__file__).parent
    env=dict(os.environ,DOCUMENT_AI_PROGRESS='1')
    def call(tool,extra):
        return subprocess.run([sys.executable,str(tools/tool),*extra],env=env).returncode
    flags=['--manifest',str(manifest),'--output',str(out/'evaluation')]
    old=Path(args.reuse_evaluation).resolve() if args.reuse_evaluation else None
    eval_code=call('refresh_evaluation.py',flags+['--evaluation',str(old)]) if old else call('evaluate_batch.py',flags)
    eval_file=out/'evaluation/evaluation.json'
    if not eval_file.exists():raise RuntimeError('Evaluation failed before writing output')
    diagnostic_input=old or eval_file
    diag_flags=['--manifest',str(manifest),'--evaluation',str(diagnostic_input),'--output',str(out/'diagnostics'),'--retry-number']
    if args.save_evidence:diag_flags+=['--save-evidence']
    diag_code=call('diagnose_batch.py',diag_flags)
    evaluated=json.loads(eval_file.read_text(encoding='utf-8-sig'))
    diag_file=out/'diagnostics/diagnostics.json'
    diagnostics=json.loads(diag_file.read_text(encoding='utf-8-sig')) if diag_file.exists() else {'cases':[]}
    diag_by_id={c['id']:c for c in diagnostics['cases']}
    source_cases=json.loads(diagnostic_input.read_text(encoding='utf-8-sig'))['cases']
    source_by_id={c['id']:c for c in source_cases}
    source_manifest={c['id']:c for c in cases}
    combined=[]
    for c in evaluated['cases']:
        item={'id':c['id'],'evaluation':c,'diagnostics':diag_by_id.get(c['id']),
              'document_validity':'NOT_VERIFIED','policy':'Original gates unchanged; recovery candidates never replace OCR automatically'}
        try:
            cfg=source_manifest[c['id']]
            rp=manifest.parent/cfg['report'] if cfg.get('report') else diagnostic_input.parent/'reader-runs'/source_by_id[c['id']]['sourceRequestId']/'report.json'
            report=json.loads(rp.read_text(encoding='utf-8-sig'))
            item['pipelineAssessment']=report.get('assessment')
            item['pipelineChecks']=report.get('checks',[])
            item['comparison']=compare_document(report)
            qr=(item['diagnostics'] or {}).get('qr',{})
            # Separate what-if comparison; original report and original comparison stay intact.
            if qr.get('status')=='PASS' and len(qr.get('payloads',[]))==1:
                recovery_report={**report,'qrPayloads':{**report.get('qrPayloads',{}),'BACK':qr['payloads']}}
                item['diagnosticQrComparison']=compare_document(recovery_report)
        except Exception as error:item['consolidation_error']=type(error).__name__
        combined.append(item)
    payload={'schemaVersion':'complete-reader/0.1','release':'0.10.0','cases':combined,
             'summary':evaluated['summary'],'wallMs':round((time.perf_counter()-start)*1000,2),
             'reusedInference':bool(old),'authenticity':'NOT_IMPLEMENTED_VALIDATED_MODEL',
             'scope':'Reader pipeline laboratory, not production eKYC. No automatic identity or legal validity decision.'}
    (out/'complete-report.json').write_text(dumps(payload),encoding='utf-8')
    columns=['case','pipeline','qr_gate','qr_original','qr_aligned','successful_sources','number_retry_count','errors']
    rows=[]
    for item in combined:
        d=item['diagnostics'] or {};q=d.get('qr',{});sources=q.get('sources',{})
        rows.append({'case':item['id'],'pipeline':item.get('pipelineAssessment','ERROR'),'qr_gate':item['evaluation'].get('qr_state','UNKNOWN'),
                     'qr_original':sources.get('original',{}).get('status','UNKNOWN'),'qr_aligned':sources.get('aligned',{}).get('status','UNKNOWN'),
                     'successful_sources':', '.join(q.get('successful_sources',[])),'number_retry_count':len(d.get('number_retries',[])),
                     'errors':bool(d.get('error') or item.get('consolidation_error') or item['evaluation'].get('status')=='ERROR' or q.get('status')=='ERROR')})
    with (out/'summary.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=columns);writer.writeheader();writer.writerows(rows)
    esc=lambda value:html.escape(str(value))
    table='<table><thead><tr>'+''.join('<th>'+esc(k)+'</th>' for k in columns)+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+esc(row[k])+'</td>' for k in columns)+'</tr>' for row in rows)+'</tbody></table>'
    details=''.join('<details><summary>'+esc(c['id'])+'</summary><pre>'+esc(dumps(c))+'</pre></details>' for c in combined)
    page='<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>DocumentAI reader report</title><style>body{font:15px system-ui;margin:32px;color:#18302a}table{border-collapse:collapse;width:100%;display:block;overflow:auto}td,th{border:1px solid #ccd8d1;padding:10px;text-align:left}th{background:#eef6f0}pre{white-space:pre-wrap;overflow-wrap:anywhere}details{margin:16px 0}h1{font-size:26px}</style><h1>DocumentAI — báo cáo reader</h1><p>Baseline và diagnostic được giữ riêng. PASS giải mã QR không chứng minh giấy tờ thật. Báo cáo chứa dữ liệu cá nhân; lưu cục bộ.</p>'+table+details+'</html>'
    (out/'review.html').write_text(page,encoding='utf-8')
    print('COMPLETE:',out/'complete-report.json',flush=True)
    return 1 if eval_code or diag_code or any(r['errors'] for r in rows) else 0


if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--manifest',required=True);p.add_argument('--output',required=True)
    p.add_argument('--reuse-evaluation');p.add_argument('--save-evidence',action='store_true')
    sys.exit(run(p.parse_args()))
