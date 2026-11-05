from document_ai.batch_evaluation import evaluate_report, summarize


def test_missing_ocr_does_not_borrow_qr_answers():
    expected = dict(licenseNo='123456789012', fullName='NGUYỄN AN', dateOfBirth='2000-01-02', licenseClass='A1')
    result = evaluate_report({'requestId': 'test', 'ocrLines': {}, 'checks': []}, expected)
    assert all(r['exact_match'] is False for r in result['fields'])
    assert all(r['ocr_value'] is None for r in result['fields'])


def test_unlabeled_not_counted_and_errors_reported():
    result = evaluate_report({'ocrLines': {}, 'checks': []}, {})
    result.update(status='DONE')
    summary = summarize([result, {'status': 'ERROR'}])
    assert summary['case_errors'] == 1
    assert summary['completed_count'] == 1
    assert summary['fields']['fullName']['exact_accuracy'] is None
