"""Ground truth is used only AFTER inference. Metrics measure reading, not identity."""
import unicodedata
from document_ai.document_comparison import compare_document
from document_ai.name_comparison import unaccent_name

FIELDS = ('licenseNo', 'fullName', 'dateOfBirth', 'licenseClass')


def evaluate_report(report, expected):
    comparison = compare_document(report)
    rows = []
    for key in FIELDS:
        entry = comparison['ocrData'][key]
        candidate = entry.get('candidate') or {}
        value = candidate.get('value')
        truth = expected.get(key)
        labeled = isinstance(truth, str) and bool(truth)
        exact = (unicodedata.normalize('NFC', value) == unicodedata.normalize('NFC', truth)) if labeled and isinstance(value, str) else False
        rows.append({'field': key, 'labeled': labeled, 'expected': truth,
                     'ocr_value': value, 'extraction_status': entry['status'],
                     'exact_match': exact if labeled else None,
                     'unaccented_match': (bool(value) and unaccent_name(value) == unaccent_name(truth)) if labeled and key == 'fullName' else None,
                     'confidence': candidate.get('confidence'), 'invalid_candidates': entry.get('invalid_candidates', [])})
    qr = comparison['qrData']
    checks=report.get('checks',[])
    back=next((c for c in checks if c.get('check')=='QR' and str(c.get('image_id','')).lower()=='back'),None)
    quality=next((c for c in checks if c.get('check') in {'QUALITY','ALIGNED_QUALITY'} and c.get('status')=='FAIL' and str(c.get('image_id','')).lower()=='back'),None)
    state='UNKNOWN'
    if back:
        status=back.get('status')
        if status=='ERROR':state='ERROR'
        elif status=='NOT_APPLICABLE':state='NOT_APPLICABLE'
        elif status=='SKIPPED':state='BLOCKED_BY_QUALITY' if quality and quality.get('status')=='FAIL' else 'SKIPPED'
        elif report.get('qrPayloads',{}).get('BACK'):state='DECODED'
        else:state='NOT_DECODED'
    elif report.get('qrPayloads',{}).get('BACK'):state='DECODED'

    return {'sourceRequestId': report.get('requestId'), 'fields': rows,
            'qr_state': state, 'quality': [{'side': c.get('image_id'), 'check': c.get('check'), 'status': c.get('status'), 'reasons': c.get('reasons'), 'metrics': c.get('metrics')} for c in checks if c.get('check') in {'QUALITY','ALIGNED_QUALITY'}],
            'qr_parsed': qr['status'] == 'PARSED',
            'qr_exact_match': all(qr.get('fields', {}).get(k) == expected[k] for k in FIELDS if k in expected) if all(k in expected for k in FIELDS) else None,
            'elapsedMs': report.get('elapsedMs'),
            'timings': [{'side': c.get('image_id'), 'check': c.get('check'), 'duration_ms': c.get('duration_ms')} for c in report.get('checks', []) if c.get('check') in ('OCR', 'QR')],
            'errors': [{'side': c.get('image_id'), 'check': c.get('check')} for c in report.get('checks', []) if c.get('status') == 'ERROR']}


def summarize(cases):
    summary = {'case_count': len(cases), 'case_errors': sum(c.get('status') == 'ERROR' for c in cases), 'fields': {}}
    for key in FIELDS:
        rows = [r for c in cases for r in c.get('fields', []) if r['field'] == key and r['labeled']]
        count = len(rows)
        summary['fields'][key] = {'labeled_count': count, 'exact_correct': sum(r['exact_match'] for r in rows),
            'exact_accuracy': sum(r['exact_match'] for r in rows) / count if count else None,
            'missing_or_ambiguous': sum(r['extraction_status'] != 'EXTRACTED' for r in rows)}
        if key == 'fullName':
            summary['fields'][key]['unaccented_correct'] = sum(bool(r['unaccented_match']) for r in rows)
    completed = [c for c in cases if c.get('status') == 'DONE']
    summary['qr_states'] = {state: sum(c.get('qr_state')==state for c in completed) for state in ('DECODED','NOT_DECODED','BLOCKED_BY_QUALITY','SKIPPED','ERROR','NOT_APPLICABLE','UNKNOWN')}
    summary['completed_count'] = len(completed)
    summary['qr_parsed_count'] = sum(c['qr_parsed'] for c in completed)
    summary['note'] = 'Report-level reading metrics; case errors are separate, missing extraction counts as incorrect. No authenticity score. Same-card photos are correlated.'
    return summary
