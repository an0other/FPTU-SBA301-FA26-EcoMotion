from document_ai.batch_evaluation import evaluate_report
from document_ai.document_comparison import extract_pilot


def test_quality_blocked_is_not_decode_failure():
    r={'checks':[{'image_id':'back','check':'QUALITY','status':'FAIL','reasons':['BLUR']},{'image_id':'back','check':'QR','status':'SKIPPED'}]}
    assert evaluate_report(r,{})['qr_state']=='BLOCKED_BY_QUALITY'


def test_number_missing_digit_retained_not_repaired():
    r=extract_pilot([{'text':'Só/No:12345678901','confidence':.99,'polygon':[]}])['licenseNo']
    assert r['status']=='INVALID_FORMAT'
    assert r['candidate'] is None
    assert r['invalid_candidates'][0]['value']=='12345678901'


def test_decoder_ran_but_empty():
    r={'checks':[{'image_id':'back','check':'QR','status':'INCONCLUSIVE'}]}
    assert evaluate_report(r,{})['qr_state']=='NOT_DECODED'
