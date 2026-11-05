"""Independent QR and number-crop experiments; original reports never modified."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse,json,hashlib,re,time
from document_ai.core import dumps
from document_ai.imaging import decode
from document_ai.adapters.qr import scan_qr
from document_ai.document_comparison import extract_pilot
from document_ai.recognition import create_line_recognizer
from document_ai.diagnostics import aligned_from_report, compare_qr_sources


def bound_image(path,report,side):
    with path.open('rb') as f:raw=f.read(8388609)
    record=next(i for i in report['images'] if i['side']==side)
    if hashlib.sha256(raw).hexdigest()!=record['sha256']:raise ValueError('SOURCE_HASH_MISMATCH')
    decoded=decode(raw,{})
    if decoded.status!='PASS':raise ValueError('INVALID_IMAGE')
    return decoded.payload['image']


def main():
    p=argparse.ArgumentParser()
    for key in ('manifest','evaluation','output'):p.add_argument('--'+key,required=True)
    p.add_argument('--retry-number',action='store_true')
    p.add_argument('--save-evidence',action='store_true');a=p.parse_args()
    manifest_path=Path(a.manifest).resolve();base=manifest_path.parent
    cases=json.loads(manifest_path.read_text(encoding='utf-8-sig'))['cases']
    evaluation=Path(a.evaluation).resolve()
    prior={c['id']:c for c in json.loads(evaluation.read_text(encoding='utf-8-sig'))['cases']}
    out=Path(a.output);out.mkdir(parents=True,exist_ok=False)
    results=[];engine=None
    for index,case in enumerate(cases):
        print(f'Diagnostic {index+1}/{len(cases)}',flush=True)
        item={'id':case['id'],'diagnostic_only':True,'document_validity':'NOT_VERIFIED'}
        try:
            prev=prior[case['id']]
            source=base/case['report'] if case.get('report') else evaluation.parent/'reader-runs'/prev['sourceRequestId']/'report.json'
            report=json.loads(source.read_text(encoding='utf-8-sig'));item['sourceRequestId']=report['requestId']
            if not case.get('back'):item['qr']={'status':'SKIPPED','reason':'BACK_IMAGE_PATH_REQUIRED'}
            else:
                back=bound_image(base/case['back'],report,'BACK');start=time.perf_counter()
                item['qr']=compare_qr_sources(back,aligned_from_report(back,report,'BACK'),scan_qr,out/'evidence'/str(index+1) if a.save_evidence else None)
                item['qr']['duration_ms']=(time.perf_counter()-start)*1000
            entry=extract_pilot(report.get('ocrLines',{}).get('FRONT',[]))['licenseNo']
            item['number_original']=entry;item['number_retries']=[]
            if a.retry_number and entry['status']!='EXTRACTED':
                if not case.get('front'):item['number_retry_reason']='FRONT_IMAGE_PATH_REQUIRED'
                else:
                    front=bound_image(base/case['front'],report,'FRONT')
                    aligned=aligned_from_report(front,report,'FRONT')
                    if aligned is not None:front=aligned
                    item['number_crop_coordinate_space']='stored_aligned' if aligned is not None else 'exif_oriented_original'
                    for candidate in entry.get('invalid_candidates',[])[:2]:
                        pts=candidate.get('polygon') or []
                        if len(pts)!=4:continue
                        xs=[pt[0] for pt in pts];ys=[pt[1] for pt in pts];h=max(ys)-min(ys)
                        for margin in (.25,.5):
                            box=(max(0,int(min(xs)-h*margin)),max(0,int(min(ys)-h*.15)),min(front.width,int(max(xs)+h*margin)),min(front.height,int(max(ys)+h*.15)))
                            if box[2]<=box[0] or box[3]<=box[1]:continue
                            if engine is None:engine=create_line_recognizer('paddle-v6-tiny')
                            crop=front.crop(box)
                            if a.save_evidence:
                                folder=out/'evidence'/str(index+1)/'number';folder.mkdir(parents=True,exist_ok=True)
                                crop.save(folder/f'line-{candidate["line_index"]}-margin-{margin}.png')
                            start=time.perf_counter();text,confidence=engine.recognize(crop)
                            item['number_retries'].append({'original_line_index':candidate['line_index'],'crop_box':box,'text':text,'confidence':confidence,'format_valid_candidates':re.findall(r'(?<![0-9])[0-9]{12}(?![0-9])',text),'duration_ms':(time.perf_counter()-start)*1000})
                    item['number_retry_reason']='CANDIDATES_ONLY_NO_AUTOMATIC_REPLACEMENT'
        except Exception as error:
            item['error']={'exception_type':type(error).__name__,'reason':str(error) if str(error) in {'SOURCE_HASH_MISMATCH','INVALID_IMAGE'} else 'CASE_EXECUTION_ERROR'}
        results.append(item)
    (out/'diagnostics.json').write_text(dumps({'cases':results,'note':'Separate diagnostics. Quality gate unchanged. No expected answers or QR values used to repair OCR; no automatic candidate selection.'}),encoding='utf-8')
    print(out/'diagnostics.json')
    return 1 if any('error' in c or c.get('qr',{}).get('status')=='ERROR' for c in results) else 0


if __name__=='__main__':sys.exit(main())
