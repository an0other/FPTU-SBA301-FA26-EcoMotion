"""Explicit pilot for observed 5-line GPLX QR. No issuer validation."""
import re
from datetime import datetime
from document_ai.name_comparison import compare_names,unaccent_name


def parse_qr(payloads):
    result={'schema':'observed-gplx-five-lines/v1','status':'INCONCLUSIVE','fields':{},'reasons':[]}
    if len(payloads)!=1:
        result['reasons']=['QR_MISSING_OR_AMBIGUOUS'];return result
    # Accept LF or CRLF; one trailing line separator is harmless, internal empty lines are not.
    parts=payloads[0].splitlines()
    if len(parts)!=5 or any(not s.strip() for s in parts):
        result['reasons']=['UNSUPPORTED_QR_LAYOUT'];return result
    number,name,birth,category,region=[s.strip() for s in parts]
    errors=[]
    if not re.fullmatch(r'[0-9]{12}',number):errors.append('INVALID_NUMBER_FORMAT')
    if not any(c.isalpha() for c in name):errors.append('INVALID_NAME_FORMAT')
    try:dob=datetime.strptime(birth,'%d/%m/%Y').date().isoformat()
    except ValueError:dob=None;errors.append('INVALID_BIRTH_DATE')
    if not re.fullmatch(r'[A-Z][0-9]?',category):errors.append('UNSUPPORTED_CLASS_FORMAT')
    result.update(status='PARSED' if not errors else 'INCONCLUSIVE',reasons=errors,
        fields={'licenseNo':number,'fullName':name,'dateOfBirth':dob,'licenseClass':category,
                'regionRaw':region},
        note='regionRaw semantics unconfirmed; class format is not a legal eligibility check')
    return result


def box(line):
    pts=line.get('polygon',[])
    if not pts:return None
    return min(p[0] for p in pts),min(p[1] for p in pts),max(p[0] for p in pts),max(p[1] for p in pts)


def field(value,line,index,method):
    return {'value':value,'raw_text':line['text'],'confidence':line.get('confidence'),
            'line_index':index,'polygon':line.get('polygon'),'method':method,
            'recognition_status':line.get('recognition_status','UNKNOWN')}


def extract_pilot(lines):
    candidates={key:[] for key in ['licenseNo','fullName','dateOfBirth','licenseClass']}
    labels=[]
    for index,line in enumerate(lines):
        text=line['text'];flat=unaccent_name(text)
        if re.search(r'(?:\bNO\b|\bSO\b)\s*[:/.]?',flat):
            numbers=re.findall(r'(?<![0-9])[0-9]{12}(?![0-9])',text)
            if len(numbers)==1:candidates['licenseNo'].append(field(numbers[0],line,index,'number-label'))
        if 'CLASS' in flat or 'HANG' in flat:
            match=re.search(r'(?:CLASS|HANG)\s*[:/.]?\s*([A-Z][0-9]?)\b',flat)
            if match:candidates['licenseClass'].append(field(match.group(1),line,index,'class-label'))
        if 'FULL NAME' in flat or 'FULLNAME' in flat:labels.append(('fullName',index,line))
        if 'DATE OF BIRTH' in flat:labels.append(('dateOfBirth',index,line))
    for key,_,label in labels:
        anchor=box(label)
        if anchor is None:continue
        ax1,ay1,ax2,ay2=anchor;ah=max(1,ay2-ay1)
        for index,line in enumerate(lines):
            bounds=box(line)
            if bounds is None:continue
            x1,y1,x2,y2=bounds
            # Only adjacent right-hand values in the SAME row for this pilot.
            if x1<ax2-ah or abs((y1+y2-ay1-ay2)/2)>ah*1.3:continue
            text=line['text'].strip();value=None
            if key=='dateOfBirth' and re.fullmatch(r'\d{2}/\d{2}/\d{4}',text):
                try:value=datetime.strptime(text,'%d/%m/%Y').date().isoformat()
                except ValueError:pass
            if key=='fullName' and any(c.isalpha() for c in text) and not any(c.isdigit() for c in text) and not any(c in text for c in ':/'):
                value=text
            if value:candidates[key].append(field(value,line,index,'right-of-label'))
    invalid=[]
    for index,line in enumerate(lines):
        if re.search(r'(?:\bNO\b|\bSO\b)',unaccent_name(line['text'])):
            tokens=re.findall(r'[0-9]+',line['text'])
            for token in tokens:
                if 6<=len(token)<=16 and len(token)!=12:
                    invalid.append(field(token,line,index,'number-label-invalid-format'))
    result={}
    for key,items in candidates.items():
        unique={item['line_index']:item for item in items}
        result[key]={'status':'EXTRACTED' if len(unique)==1 else 'NOT_FOUND' if not unique else 'AMBIGUOUS',
                     'candidate':next(iter(unique.values())) if len(unique)==1 else None,
                     'candidates':list(unique.values())}
    result['licenseNo']['invalid_candidates']=invalid
    if result['licenseNo']['status']=='NOT_FOUND' and invalid:
        result['licenseNo']['status']='INVALID_FORMAT'
    return result


def compare_document(report,name_mode='unaccented',min_confidence=.9):
    if name_mode not in {'strict','unaccented'}:raise ValueError('Unsupported name mode')
    if not 0<=min_confidence<=1:raise ValueError('Invalid confidence threshold')
    qr=parse_qr(report.get('qrPayloads',{}).get('BACK',[]))
    ocr=extract_pilot(report.get('ocrLines',{}).get('FRONT',[]))
    comparisons=[]
    for key,entry in ocr.items():
        candidate=entry['candidate'];target=qr['fields'].get(key)
        item={'field':key,'status':'INCONCLUSIVE','text_result':None,'reasons':[],
              'ocr':candidate,'qr_value':target,'identity_verified':False}
        if qr['status']!='PARSED':item['reasons'].append('QR_NOT_PARSED')
        elif candidate is None:item['reasons'].append('OCR_'+entry['status'])
        else:
            if key=='fullName':
                detail=compare_names(candidate['value'],target)
                matched=detail['exact_match'] if name_mode=='strict' else detail['accent_insensitive_match']
                item['name_comparison']=detail
            else:matched=candidate['value']==target
            item['text_result']='MATCH' if matched else 'DIFFERENT'
            score=candidate.get('confidence')
            if score is None or score<min_confidence:item['reasons'].append('OCR_CONFIDENCE_BELOW_PILOT_THRESHOLD')
            if candidate.get('recognition_status') in {'DISAGREEMENT','SECONDARY_ERROR'}:item['reasons'].append('RECOGNIZER_UNCERTAINTY')
            if not item['reasons']:item['status']='CONSISTENT_TEXT' if matched else 'DIFFERENT_TEXT'
        comparisons.append(item)
    return {'schemaVersion':'document-comparison/0.1','sourceRequestId':report.get('requestId'),
            'assessment':'DIAGNOSTIC_ONLY','qrData':qr,'ocrData':ocr,'comparisons':comparisons,
            'policy':{'name_mode':name_mode,'min_confidence':min_confidence,'threshold_calibrated':False},
            'uncomparedFields':['regionRaw'],'document_validity':'NOT_VERIFIED',
            'note':'Pilot label/geometry extraction; unknown layouts remain inconclusive. Differences may be OCR errors. No original report is modified.'}
