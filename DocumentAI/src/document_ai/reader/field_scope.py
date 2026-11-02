"""Opt-in GPLX reading scope; unknown layouts/type clues always use full reading."""
import json,os
from pathlib import Path
from .data import title_type
from .field_regions import boxes
from .orientation import CLUES
from document_ai.name_comparison import unaccent_name


def select_field_regions(regions,image,profile_path,side,expected_type,orientation_lines):
    full={'status':'FULL','detectedCount':len(regions),'recognizedCount':len(regions),'skippedRegions':[]}
    if expected_type=='VN_CITIZEN_ID' and side=='BACK':
        compact=''.join(''.join(unaccent_name(l.get('text') or '').split()) for l in orientation_lines)
        supported='DACDIEMNHANDANG' in compact or ('LEFTINDEXFINGER' in compact and 'RIGHTINDEXFINGER' in compact)
        if supported:
            import math
            kept=[];skipped=[]
            for region in regions:
                p=region['polygon'];cy=sum(v[1] for v in p)/4
                w=math.dist(p[0],p[1]);h=max(1,math.dist(p[0],p[3]))
                if cy>image.height*.65 and w/h>=18:
                    skipped.append({'polygon':p,'status':'NOT_RECOGNIZED','reason':'LONG_BOTTOM_LINE_OUTSIDE_SUPPORTED_FIELD_SCOPE'})
                else:kept.append(region)
            if kept and skipped:
                return kept,{'status':'APPLIED','side':side,'detectedCount':len(regions),'recognizedCount':len(kept),
                    'skippedRegions':skipped,'scope':'CCCD_BACK_FIELDS_EXCLUDING_LONG_BOTTOM_LINES',
                    'mrzVerified':False,'validated':False}
        return regions,{**full,'reason':'NO_SUPPORTED_BACK_SCOPE_OR_NO_EXCLUDED_LINES'}
    if expected_type!='VN_DRIVING_LICENSE' or not profile_path or side not in {'FRONT','BACK'}:
        return regions,full
    profile=json.loads(Path(profile_path).read_text(encoding='utf-8-sig'))
    if profile.get('documentType')!=expected_type:return regions,{**full,'reason':'PROFILE_TYPE_MISMATCH'}
    text=' '.join(unaccent_name(l['text']) for l in orientation_lines if l.get('text'))
    back_aliases=next(a for n,a,_ in CLUES if n=='gplx_back')
    if side=='FRONT':supported=title_type(orientation_lines)==expected_type
    else:supported=title_type(orientation_lines) is None and any(a in text for a in back_aliases)
    if not supported:return regions,{**full,'reason':'LAYOUT_CLUE_NOT_CONFIRMED'}
    scope_mode=os.getenv('DOCUMENT_AI_FIELD_SCOPE','full')
    if scope_mode not in {'full','core'}:raise ValueError('Invalid field scope')
    core={'documentNumber','fullName','dateOfBirth','licenseClass'}
    rois=[box for key,box in boxes(profile,image.size).items() if scope_mode!='core' or key in core] if side=='FRONT' else []
    kept=[];skipped=[]
    for region in regions:
        points=region['polygon'];cx=sum(p[0] for p in points)/4;cy=sum(p[1] for p in points)/4
        include=cy<=image.height*(.30 if scope_mode=='core' and side=='FRONT' else .40) or any(x1<=cx<=x2 and y1<=cy<=y2 for x1,y1,x2,y2 in rois)
        if include:kept.append(region)
        else:skipped.append({'polygon':points,'status':'NOT_RECOGNIZED','reason':'OUTSIDE_CONFIGURED_FIELD_SCOPE'})
    if not kept:return regions,{**full,'reason':'NO_REGIONS_SELECTED'}
    return kept,{'status':'APPLIED','side':side,'detectedCount':len(regions),
                 'recognizedCount':len(kept),'skippedRegions':skipped,'validated':False,'fieldScope':scope_mode}
