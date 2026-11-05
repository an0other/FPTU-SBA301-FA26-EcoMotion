"""Ground-truth evaluation AFTER inference; QR never supplies OCR labels."""
import json
import re
import unicodedata
from pathlib import Path
from collections import Counter
from .data import COMMON, EXTRA, TYPES, date
from document_ai.name_comparison import unaccent_name

FIELDS = set(COMMON) | set().union(*EXTRA.values())
STATUSES = {'READ_SUCCESS','PARTIALLY_READ','RETAKE_REQUIRED','UNSUPPORTED_DOCUMENT',
            'INCONSISTENT_DATA','REVIEW_REQUIRED','PROCESSING_ERROR'}


def load_cases(path):
    path = Path(path).resolve()
    manifest = json.loads(path.read_text(encoding='utf-8-sig'))
    cases = manifest.get('cases')
    if not isinstance(cases,list) or not cases: raise ValueError('Nonempty cases required')
    ids = [c.get('id') for c in cases]
    if any(not isinstance(i,str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,80}',i) for i in ids) or len(set(ids)) != len(ids):
        raise ValueError('Unique case IDs: letters, digits, underscore or hyphen, max 80')
    result=[]
    for case in cases:
        c=dict(case)
        if c.get('report'): raise ValueError('Image inference cases only; report paths are not accepted')
        for side in ('front','back'):
            if not isinstance(c.get(side),str): raise ValueError('Each case requires front and back')
            c[side]=str((path.parent/c[side]).resolve())
            if not Path(c[side]).is_file(): raise ValueError('Missing image: '+c[side])
        c['documentType']=c.get('documentType',manifest.get('documentType','VN_DRIVING_LICENSE'))
        if c['documentType'] not in ('AUTO',)+TYPES: raise ValueError('Unsupported documentType')
        labels=c.get('expected',{})
        if not isinstance(labels,dict) or not labels: raise ValueError('Each case needs manually checked expected labels')
        labels=dict(labels)
        if 'licenseNo' in labels:
            if 'documentNumber' in labels and labels['documentNumber'] != labels['licenseNo']:
                raise ValueError('Conflicting licenseNo/documentNumber labels')
            labels['documentNumber']=labels.pop('licenseNo')
        for key,value in labels.items():
            if key not in FIELDS or not isinstance(value,str) or not value.strip(): raise ValueError('Invalid expected field: '+key)
            if any(marker in value.upper() for marker in ('FILL_','REPLACE_','TODO','YOUR_')):
                raise ValueError('Replace placeholder label for '+key)
            if key in ('dateOfBirth','issueDate','expiryDate'):
                if key=='expiryDate' and value=='UNLIMITED': continue
                parsed=date(value)
                if not parsed: raise ValueError('Invalid labeled date: '+key)
                labels[key]=parsed
        statuses=c.get('expectedStatuses')
        if statuses is not None and (not isinstance(statuses,list) or not statuses or any(s not in STATUSES for s in statuses)):
            raise ValueError('expectedStatuses must be a nonempty list of known statuses')
        if 'expectedQrParsed' in c and type(c['expectedQrParsed']) is not bool:
            raise ValueError('expectedQrParsed must be boolean')
        c['expected']=labels;result.append(c)
    return result


def exact(left,right):
    return isinstance(left,str) and unicodedata.normalize('NFC',left)==unicodedata.normalize('NFC',right)


def evaluate(response,case):
    rows=[]
    for field,truth in case['expected'].items():
        entry=response.get('ocrData',{}).get(field,{})
        candidate=entry.get('candidate') or {}
        ocr=candidate.get('value')
        qr=response.get('qrData',{}).get('fields',{}).get(field)
        final=response.get('data',{}).get(field,response.get('additionalData',{}).get(field))
        rows.append({'field':field,'expected':truth,'ocrValue':ocr,'qrValue':qr,'finalValue':final,
            'ocrStatus':entry.get('status','NOT_AVAILABLE'),'ocrExact':exact(ocr,truth),
            'ocrUnaccented':bool(ocr) and unaccent_name(ocr)==unaccent_name(truth) if field=='fullName' else None,
            'qrExact':exact(qr,truth),'finalExact':exact(final,truth),'confidence':candidate.get('confidence')})
    parsed=response.get('qrData',{}).get('status')=='PARSED'
    expected_status=case.get('expectedStatuses')
    return {'fields':rows,'status':response.get('status','PROCESSING_ERROR'),
        'statusReasons':response.get('statusReasons',[]),'qrParsed':parsed,
        'expectedStatuses':expected_status,
        'statusExpectationMet':response.get('status') in expected_status if expected_status else None,
        'qrExpectationMet':parsed==case['expectedQrParsed'] if 'expectedQrParsed' in case else None,
        'warnings':response.get('warnings',[]),'qrSearchCoverage':response.get('qrData',{}).get('searchCoverage'),
        'scopeReport':response.get('recognitionScopeReport',[])}


def summarize(rows):
    fields={}
    keys=sorted({f['field'] for r in rows for f in r['fields']})
    for key in keys:
        samples=[f for r in rows for f in r['fields'] if f['field']==key]
        n=len(samples)
        fields[key]={'labeledCount':n,'ocrExactCorrect':sum(f['ocrExact'] for f in samples),
            'ocrExactAccuracy':sum(f['ocrExact'] for f in samples)/n,
            'ocrMissingOrAmbiguous':sum(f['ocrValue'] is None for f in samples),
            'qrExactCorrect':sum(f['qrExact'] for f in samples),
            'finalExactCorrect':sum(f['finalExact'] for f in samples),
            'finalExactAccuracy':sum(f['finalExact'] for f in samples)/n}
        if key=='fullName':fields[key]['ocrUnaccentedCorrect']=sum(bool(f['ocrUnaccented']) for f in samples)
    return {'caseCount':len(rows),'processingErrors':sum(r['status']=='PROCESSING_ERROR' for r in rows),
        'ocrExercisedCaseCount':sum(any(c.get('step')=='OCR' and c.get('status')=='PASS' for c in r.get('checks',[])) for r in rows),
        'qualityEarlyExitCount':sum(r.get('executionReport',{}).get('exitStage')=='QUALITY_CHECK' for r in rows),
        'statuses':dict(Counter(r['status'] for r in rows)),
        'qrParsedCount':sum(r['qrParsed'] for r in rows),'fields':fields,
        'statusExpectationFailures':sum(r['statusExpectationMet'] is False for r in rows),
        'qrExpectationFailures':sum(r['qrExpectationMet'] is False for r in rows)}
