"""Reconstruct the EXACT stored transform on an already hash-validated original."""
import cv2
import numpy as np
from PIL import Image


def aligned_from_report(image,report,side):
    records=[t for t in report.get('transforms',[]) if str(t.get('image_id','')).upper()==side.upper()]
    if not records:return None
    if len(records)!=1:raise ValueError('AMBIGUOUS_TRANSFORM')
    t=records[0]
    if t.get('from')!='exif_oriented_original' or t.get('to')!='manual_aligned':raise ValueError('UNSUPPORTED_TRANSFORM')
    matrix=np.asarray(t['homography'],dtype=float)
    w,h=t['width'],t['height']
    if not isinstance(w,int) or not isinstance(h,int) or not 2<=w<=2000 or not 2<=h<=2000:raise ValueError('INVALID_TRANSFORM_SIZE')
    if matrix.shape!=(3,3) or not np.isfinite(matrix).all() or abs(np.linalg.det(matrix))<1e-12:raise ValueError('INVALID_TRANSFORM')
    return Image.fromarray(cv2.warpPerspective(np.asarray(image.convert('RGB')),matrix,(w,h)))


def compare_qr_sources(original,aligned,scanner,evidence_dir=None):
    results={}
    for name,image in [('original',original),('aligned',aligned)]:
        if image is None:
            results[name]={'status':'SKIPPED','reason':'NO_STORED_TRANSFORM'};continue
        try:
            if evidence_dir is not None:
                folder=evidence_dir/name
                folder.mkdir(parents=True,exist_ok=True)
                image.save(folder/'source.png')
                result=scanner(image,{},save_evidence=True)
                for filename,content in result.evidence.items():
                    from pathlib import Path
                    if Path(filename).name!=filename:raise ValueError('Unsafe evidence filename')
                    (folder/filename).write_bytes(content)
            else:result=scanner(image,{})
            results[name]={'status':result.status,'reasons':result.reasons,'payloads':result.payload['values'],'metrics':result.metrics}
        except Exception as error:results[name]={'status':'ERROR','exception_type':type(error).__name__}
    values=sorted({v for r in results.values() for v in r.get('payloads',[])})
    errors=any(r['status']=='ERROR' for r in results.values())
    return {'status':'INCONCLUSIVE' if len(values)>1 else 'PASS' if values and not errors else 'ERROR' if errors else 'INCONCLUSIVE',
        'payloads':values,'sources':results,'successful_sources':[name for name,r in results.items() if r.get('payloads')],
        'payload_conflict':len(values)>1,'quality_bypassed_for_diagnostic':True}
