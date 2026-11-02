"""Bounded GPLX field rescue. No QR, spelling repair or confidence voting."""
import copy
import json
from pathlib import Path
from .field_regions import boxes,observe_profile

CORE=('documentNumber','fullName','dateOfBirth','licenseClass')


def rescue_missing(image,lines,parsed,profile_path,ocr,max_fields=2):
    # Ambiguous valid observations cannot be made true by reading until one wins.
    missing=[key for key in CORE if not parsed.get(key,{}).get('candidate')]
    if any(parsed.get(key,{}).get('status')=='AMBIGUOUS' for key in missing):
        return lines,parsed,{'status':'SKIPPED','reason':'VALID_AMBIGUITY_RETAINED','attempts':[]}
    if not missing or len(missing)>max_fields:
        return lines,parsed,{'status':'SKIPPED','reason':'NO_MISSING_FIELDS' if not missing else 'TOO_MANY_MISSING_FIELDS','attempts':[]}
    profile=json.loads(Path(profile_path).read_text(encoding='utf-8-sig'))
    if profile.get('documentType')!='VN_DRIVING_LICENSE':raise ValueError('Targeted rescue only supports configured GPLX profile')
    regions=boxes(profile,image.size);result=copy.deepcopy(lines);attempts=[]
    for key in missing:
        if key not in regions:continue
        x1,y1,x2,y2=regions[key]
        if x2-x1<2 or y2-y1<2:continue
        try:
            observed=ocr.extract(image.crop((x1,y1,x2,y2)))
            for row in observed:
                row=copy.deepcopy(row)
                if not row.get('polygon'):continue
                row['polygon']=[[x+x1,y+y1] for x,y in row['polygon']]
                row.update(side='FRONT',rescueField=key,rescueSource='CONFIGURED_FIELD_ROI')
                result.append(row)
            attempts.append({'field':key,'status':'COMPLETED','lineCount':len(observed),'roi':regions[key]})
        except Exception as error:
            if getattr(error,'fatal_for_request',False):raise
            attempts.append({'field':key,'status':'ERROR','errorType':type(error).__name__})
    current=observe_profile(result,'VN_DRIVING_LICENSE',image,profile_path)
    return result,current,{'status':'COMPLETED','attempts':attempts,'qrUsed':False,
        'remainingFields':[key for key in CORE if not current.get(key,{}).get('candidate')],
        'scope':'SUPPORTED_LOCALIZED_GPLX_FRONT_ONLY'}
