"""Experimental value-ROI reads for the confirmed GPLX pilot only.

Retains header/address/footer regions. Missing core invokes fresh detected OCR
through the staged fallback. No CCCD coordinate profile is invented here.
"""
import json
from pathlib import Path
from .field_regions import boxes


def profile_regions(image,regions,profile_path):
    profile=json.loads(Path(profile_path).read_text(encoding='utf-8-sig'))
    if profile.get('documentType')!='VN_DRIVING_LICENSE':return regions,{'status':'NOT_APPLIED','reason':'PROFILE_TYPE_MISMATCH'}
    fields=boxes(profile,image.size)
    selected={k:fields[k] for k in ('documentNumber','fullName','dateOfBirth','nationality','licenseClass') if k in fields}
    kept=[];skipped=[]
    for row in regions:
        p=row['polygon'];cx=sum(v[0] for v in p)/4;cy=sum(v[1] for v in p)/4
        if any(x1<=cx<=x2 and y1<=cy<=y2 for x1,y1,x2,y2 in selected.values()):skipped.append(row['polygon'])
        else:kept.append(row)
    for key,(x1,y1,x2,y2) in selected.items():
        kept.append({'polygon':[[x1,y1],[x2-1,y1],[x2-1,y2-1],[x1,y2-1]],
            'text':None,'confidence':None,'detectionConfidence':None,'primaryRecognition':'NOT_RUN',
            'fieldHint':key,'geometrySource':'PILOT_PROFILE_VALUE_ROI','templateValidated':False})
    return kept,{'status':'APPLIED','fields':list(selected),'replacedDetectedRegions':len(skipped),
        'originalDetectedPolygons':skipped,'recognizedRegions':len(kept),'validated':False,
        'readingSource':'ORIGINAL_PIXELS','fallback':'FRESH_DETECTED_OCR_IF_CORE_MISSING',
        'scope':'GPLX_PILOT_ONLY_NO_QR_GUIDANCE'}
