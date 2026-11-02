"""Explicit template ROIs; coordinates relative to unpadded rectified card.
Pilot template, never use as generic CCCD/license schema.
"""
import json,re,math,os
from pathlib import Path
from .data import normalize_field,ALIASES,printed_labels
from .footer_reading import parse_date_text,direct_footer_read
from document_ai.name_comparison import unaccent_name

def reading_order(lines):
    rows=[]
    for line in sorted(lines,key=lambda l:sum(p[1] for p in l['polygon'])/4):
        p=line['polygon'];cy=sum(q[1] for q in p)/4;h=max(q[1] for q in p)-min(q[1] for q in p)
        row=next((v for v in rows if abs(v[0]-cy)<=max(1,min(v[1],h))*.6),None)
        if row is None:rows.append([cy,h,[line]])
        else:row[2].append(line)
    return [l for _,_,row in rows for l in sorted(row,key=lambda l:min(p[0] for p in l['polygon']))]

def boxes(profile,size):
    w,h=size;m=profile['contextMarginFraction'];cw=w/(1+2*m);ch=h/(1+2*m)
    return {k:[max(0,round(m*cw+b[0]*cw)),max(0,round(m*ch+b[1]*ch)),min(w,round(m*cw+b[2]*cw)),min(h,round(m*ch+b[3]*ch))] for k,b in profile['fields'].items()}

def field_labels(text):
    from .label_ownership import label_keys
    return label_keys(text,ALIASES)


def owned_lines(key,lines):
    """A printed label owns its value. ROI overlap is not field ownership."""
    policy=os.getenv('DOCUMENT_AI_FIELD_LABEL_POLICY','owned')
    if policy not in {'owned','legacy'}:raise ValueError('Invalid field label policy')
    rows=[l for l in lines if l.get('recognition_status')!='ERROR']
    if policy=='legacy':return rows
    accepted=[];preferred=[]
    for row in rows:
        labels=field_labels(row['text'])
        if labels and key not in labels:continue
        accepted.append(row)
        if key in labels and key in {'licenseClass','documentNumber'} and ':' in row['text']:
            # Only prefer a complete labeled value, never an empty label over a separate value.
            if normalize_field(key,row['text'].rsplit(':',1)[-1]) is not None:preferred.append(row)
    return preferred or accepted


def parse_field_lines(key,ls):
    ls=reading_order([l for l in owned_lines(key,ls) if l.get('polygon')]);texts=[];used=[]
    if key in ('issueDate','expiryDate'):
        valid=[(parse_date_text(key,l['text']),l) for l in ls]
        valid=[(v,l) for v,l in valid if v is not None]
        unique={v for v,l in valid}
        return (next(iter(unique)),[l for v,l in valid]) if len(unique)==1 else (None,[l for v,l in valid])
    for l in ls:
        text=l['text'];flat=unaccent_name(text)
        # Remove known field-label lines, not arbitrary text with accents.
        if printed_labels(text):
            if os.getenv('DOCUMENT_AI_FIELD_LABEL_POLICY','owned')!='legacy' and key in printed_labels(text) and ':' in text:
                text=text.split(':',1)[1]
            elif key in ('documentNumber','licenseClass'):
                text=text.split(':')[-1]
            else:continue
        value=normalize_field(key,text)
        if value is not None:texts.append(value);used.append(l)
    if not texts:return None,[]
    if key=='address':return ' '.join(texts),used
    unique=list(dict.fromkeys(texts));return (unique[0],used) if len(unique)==1 else (None,used)

def observe_profile(lines,kind,image,profile_path):
    """Pure extraction for early fallback, using the final reader's parsing rules.

    No OCR calls, QR, confidence voting, image preprocessing or retries here.
    """
    profile=json.loads(Path(profile_path).read_text(encoding='utf-8-sig'))
    if profile['documentType']!=kind:raise ValueError('Profile/type mismatch')
    result={}
    for key,(x1,y1,x2,y2) in boxes(profile,image.size).items():
        selected=[]
        for line in lines:
            p=line.get('polygon')
            if not p or len(p)!=4 or line.get('recognition_status')=='ERROR':continue
            cx=sum(q[0] for q in p)/4;cy=sum(q[1] for q in p)/4
            if x1<=cx<=x2 and y1<=cy<=y2:selected.append(line)
        value,used=parse_field_lines(key,selected)
        def candidate(v,ls):
            return {'value':v,'rawText':' '.join(l['text'] for l in ls),
                'confidence':min(float(l.get('confidence') or 0) for l in ls),
                'side':'FRONT','polygon':None,'method':'TEMPLATE_ROI',
                'profile':profile['id'],'attempt':'full_card',
                'recognitionEngine':ls[0].get('recognitionEngine')} if v is not None and ls else None
        values=[]
        for line in used:
            v,_=parse_field_lines(key,[line])
            c=candidate(v,[line])
            if c and c['value'] not in [x['value'] for x in values]:values.append(c)
        chosen=candidate(value,used)
        result[key]={'status':'EXTRACTED' if chosen else 'AMBIGUOUS' if len(values)>1 else 'MISSING',
                     'candidate':chosen,'candidates':[chosen] if chosen else values}
    return result


def region_extract(lines,kind,image,profile_path,ocr,folder=None,retry_policy="always",field_keys=None):
    if retry_policy not in {"always","selective","direct_first"}:raise ValueError("Invalid retry policy")
    from PIL import ImageDraw
    profile=json.loads(Path(profile_path).read_text(encoding='utf-8-sig'))
    if kind!=profile['documentType']:raise ValueError('Field profile/document type mismatch')
    regions=boxes(profile,image.size);result={};overlay=image.copy() if folder else None;draw=ImageDraw.Draw(overlay) if folder else None
    if folder:folder=Path(folder);folder.mkdir(parents=True,exist_ok=True)
    for key,(x1,y1,x2,y2) in regions.items():
        if field_keys is not None and key not in field_keys:continue
        selected=[]
        for l in lines:
            p=l.get('polygon')
            if not p or len(p)!=4:continue
            cx=sum(q[0] for q in p)/4;cy=sum(q[1] for q in p)/4
            if x1<=cx<=x2 and y1<=cy<=y2:selected.append(l)
        if folder:
            draw.rectangle((x1,y1,x2,y2),outline='blue',width=2);draw.text((x1,y1),key,fill='blue')
            image.crop((x1,y1,x2,y2)).save(folder/(key+'.png'))
        primary,primary_used=parse_field_lines(key,selected)
        primary_confidence=min((float(l.get('confidence') or 0) for l in primary_used),default=0)
        should_retry=key in ('fullName','address','issueDate','expiryDate') and (
            retry_policy=='always' or primary is None or (key!='address' and primary_confidence<.9))
        skip_optional_retry=key=='address' and should_retry and not getattr(ocr,'allow_optional_retry',True)
        if skip_optional_retry:should_retry=False
        primary_internal_conflict=key in ('issueDate','expiryDate') and len({parse_date_text(key,l['text']) for l in primary_used if parse_date_text(key,l['text']) is not None})>1
        direct={'status':'NOT_NEEDED','attempts':[],'candidate':None,'candidates':[]}
        direct_attempted=False
        if retry_policy=='direct_first' and primary is None and not primary_internal_conflict and key in ('issueDate','expiryDate'):
            direct=direct_footer_read(image,key,profile,ocr,folder)
            direct_attempted=True
            if direct['status'] in {'EXTRACTED','AMBIGUOUS'}:should_retry=False
        retry=[];error=None
        retry_status='RUN' if should_retry else 'SKIPPED_VALID_PRIMARY' if key in ('fullName','address','issueDate','expiryDate') else 'NOT_CONFIGURED'
        if skip_optional_retry:retry_status='SKIPPED_OPTIONAL_RETRY_DISABLED'
        if direct_attempted and direct['status'] in {'EXTRACTED','AMBIGUOUS'}:retry_status='SKIPPED_DIRECT_READING_'+direct['status']
        if should_retry:
            try:
                for l in ocr.extract(image.crop((x1,y1,x2,y2))):
                    l=dict(l);l['side']='FRONT'
                    if l.get('polygon'):l['polygon']=[[p[0]+x1,p[1]+y1] for p in l['polygon']]
                    retry.append(l)
            except Exception as ex:error=type(ex).__name__;retry_status='ERROR'
        second,second_used=parse_field_lines(key,retry)
        # Store each observation before resolving disagreement. Do not repair text.
        def make_candidate(value,used,attempt):
            if value is None or not used:return None
            return {'value':value,'rawText':' '.join(l['text'] for l in used),
                    'confidence':min(float(l.get('confidence') or 0) for l in used),
                    'lineIndex':None,'side':'FRONT','polygon':None,
                    'method':'TEMPLATE_ROI','profile':profile['id'],'attempt':attempt,
                    'recognitionEngine':used[0].get('recognitionEngine')}
        first_candidate=make_candidate(primary,primary_used,'full_card')
        retry_candidate=make_candidate(second,second_used,'field_retry')
        alternatives=[c for c in (first_candidate,retry_candidate) if c is not None]
        disagree=primary is not None and second is not None and unaccent_name(primary)!=unaccent_name(second)
        candidate=first_candidate or retry_candidate
        # GPLX address is optional best-effort text: deterministic primary-first,
        # never confidence-based selection and never used to approve a document.
        if disagree and key!='address':candidate=None
        # Never overwrite an existing candidate or resolve a valid disagreement by retry voting.
        internal_conflict=key in ('issueDate','expiryDate') and (
            len({parse_date_text(key,l['text']) for l in primary_used if parse_date_text(key,l['text']) is not None})>1 or
            len({parse_date_text(key,l['text']) for l in second_used if parse_date_text(key,l['text']) is not None})>1)
        if internal_conflict:
            disagree=True;candidate=None
            for attempt,ls in [('full_card',primary_used),('field_retry',second_used)]:
                for line in ls:
                    value=parse_date_text(key,line['text'])
                    observed=make_candidate(value,[line],attempt)
                    if observed and observed not in alternatives:alternatives.append(observed)
        if os.getenv('DOCUMENT_AI_FIELD_LABEL_POLICY','owned')!='legacy' and key!='address' and key not in ('issueDate','expiryDate'):
            scalar=[]
            for attempt,ls in [('full_card',primary_used),('field_retry',second_used)]:
                for line in ls:
                    value,used=parse_field_lines(key,[line])
                    c=make_candidate(value,used,attempt)
                    if c and c['value'] not in [v['value'] for v in scalar]:scalar.append(c)
            if len(scalar)>1:
                disagree=True;candidate=None;alternatives=scalar
        if key in ('issueDate','expiryDate'):
            if candidate is None and not disagree and not direct_attempted:
                direct=direct_footer_read(image,key,profile,ocr,folder)
                direct_attempted=True
            alternatives+=direct['candidates']
            if direct['status']=='AMBIGUOUS':disagree=True;candidate=None
            elif direct['candidate'] is not None and not disagree:
                if candidate and candidate['value']!=direct['candidate']['value']:disagree=True;candidate=None
                else:candidate=candidate or direct['candidate']
            # Even one valid direct observation must not be hidden by a conflicting ROI result.
            if candidate and any(c['value']!=candidate['value'] for c in direct['candidates']):
                disagree=True;candidate=None

        result[key]={'status':'AMBIGUOUS' if disagree else 'EXTRACTED' if candidate else 'MISSING',
                     'candidate':candidate,'candidates':alternatives,'roi':regions[key],
                     'primaryText':primary,'retryValue':second,'retryDisagreed':disagree,
                     'retryError':error,'retryStatus':retry_status,'retryPolicy':retry_policy,'observations':{'fullCardLines':selected,'fieldRetryLines':retry},'directReading':direct,'bestEffort':key=='address' and candidate is not None,
                     'selectionPolicy':'PRIMARY_THEN_RETRY_NO_TEXT_REPAIR' if key=='address' else 'REQUIRE_AGREEMENT'}
        if folder:(folder/(key+'.json')).write_text(json.dumps({'selectedLines':selected,'retryLines':retry,'result':result[key]},ensure_ascii=False,indent=2),encoding='utf-8')
    if folder:overlay.save(folder/'field-regions.jpg')
    return result
