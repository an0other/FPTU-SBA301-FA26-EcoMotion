import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import pytest
from document_ai.reader.evaluation import load_cases,evaluate,summarize


def manifest(tmp_path,expected=None):
    (tmp_path/'f.jpg').write_bytes(b'front');(tmp_path/'b.jpg').write_bytes(b'back')
    case={'id':'first','front':'f.jpg','back':'b.jpg','expected':expected or {'licenseNo':'790000000001','fullName':'Nguyễn An'}}
    path=tmp_path/'manifest.json';path.write_text(json.dumps({'cases':[case]}));return path


def test_manifest_legacy_alias_and_relative_paths(tmp_path):
    path=manifest(tmp_path)
    case=load_cases(path)[0]
    assert case['expected']['documentNumber']=='790000000001'
    assert 'licenseNo' not in case['expected']
    assert case['front']==str(tmp_path/'f.jpg')


@pytest.mark.parametrize('labels',[{'fullName':'FILL_ACTUAL_NAME'}, {'dateOfBirth':'bad-date'}, {'unknown':'x'}])
def test_bad_labels_rejected_before_inference(tmp_path,labels):
    with pytest.raises(ValueError):load_cases(manifest(tmp_path,labels))


def test_qr_correct_does_not_hide_ocr_error():
    case={'expected':{'fullName':'Nguyễn An'},'expectedStatuses':['READ_SUCCESS'],'expectedQrParsed':True}
    response={'status':'READ_SUCCESS','ocrData':{'fullName':{'status':'EXTRACTED','candidate':{'value':'Nguyen An'}}},
              'data':{'fullName':'Nguyễn An'},'qrData':{'status':'PARSED','fields':{'fullName':'Nguyễn An'}}}
    result=evaluate(response,case);row=result['fields'][0]
    assert row['ocrExact'] is False and row['ocrUnaccented'] is True
    assert row['qrExact'] is True and row['finalExact'] is True
    assert result['statusExpectationMet'] is True


def test_missing_and_processing_errors_stay_in_denominator():
    case={'expected':{'documentNumber':'790000000001'}}
    ok=evaluate({'status':'READ_SUCCESS','ocrData':{'documentNumber':{'candidate':{'value':'790000000001'}}}},case)
    failed=evaluate({},case)
    result=summarize([ok,failed])
    assert result['processingErrors']==1
    assert result['fields']['documentNumber']['labeledCount']==2
    assert result['fields']['documentNumber']['ocrExactAccuracy']==.5


def test_unlabeled_status_and_qr_are_not_marked_pass():
    row=evaluate({'status':'READ_SUCCESS','qrData':{'status':'PARSED'}},{'expected':{'fullName':'A'}})
    assert row['statusExpectationMet'] is None and row['qrExpectationMet'] is None


@pytest.mark.parametrize('include_no_qr',[False,True])
def test_runner_reuses_models_and_never_passes_labels_to_inference(tmp_path,monkeypatch,include_no_qr):
    import document_ai.reader.factory as factory
    tool=Path(__file__).resolve().parents[1]/'tools/evaluate_reader_configs.py'
    spec=importlib.util.spec_from_file_location('reader_config_evaluation',tool)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    path=manifest(tmp_path)
    content=json.loads(path.read_text());second=dict(content['cases'][0]);second['id']='second';content['cases'].append(second)
    path.write_text(json.dumps(content))
    for name in ('weights','python','profile'):(tmp_path/name).write_text('test')
    readers=[]
    class Reader:
        def __init__(self):self.calls=[];self.ocr=self;self.closed=False
        def close(self):self.closed=True
        def read(self,*args):
            self.calls.append(args)
            return {'status':'READ_SUCCESS','data':{},'ocrData':{},'checks':[]}
    def build():
        r=Reader();readers.append(r);return r
    monkeypatch.setattr(factory,'configured_reader',build)
    monkeypatch.setattr(module.os,'environ',dict(module.os.environ))
    args=SimpleNamespace(manifest=str(path),output=str(tmp_path/'out'),corner_weights=str(tmp_path/'weights'),
        vietocr_python=str(tmp_path/'python'),profile=str(tmp_path/'profile'),device='cpu',cpu_threads=4,save_evidence=False,include_no_qr=include_no_qr)
    assert module.run(args)==0
    assert len(readers)==(4 if include_no_qr else 2) and all(len(r.calls)==2 and r.closed for r in readers)
    assert all(call==(b'front',b'back','VN_DRIVING_LICENSE',None) for r in readers for call in r.calls)
    report=json.loads((tmp_path/'out/evaluation.json').read_text())
    assert len(report['cases'])==(8 if include_no_qr else 4) and report['summaries']['combined']['caseCount']==2
    if include_no_qr:
        noqr=[r for r in report['cases'] if not r['qrEnabled']]
        assert len(noqr)==4 and all(r['statusExpectationMet'] is None and r['qrExpectationMet'] is None for r in noqr)
    assert (tmp_path/'out/fields.csv').exists()
