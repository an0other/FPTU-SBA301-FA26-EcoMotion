import importlib.util,json,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def test_complete_reuse_exports_json_csv_html_without_inference(tmp_path):
    report={'requestId':'fixture','assessment':'INCONCLUSIVE','ocrLines':{},'checks':[], 'images':[], 'qrPayloads':{}}
    rp=tmp_path/'report.json';rp.write_text(json.dumps(report))
    manifest=tmp_path/'manifest.json';manifest.write_text(json.dumps({'cases':[{'id':'<unsafe>','report':str(rp),'expected':{}}]}))
    prior=tmp_path/'evaluation.json';prior.write_text(json.dumps({'cases':[{'id':'<unsafe>','sourceRequestId':'fixture'}]}))
    output=tmp_path/'output'
    result=subprocess.run([sys.executable,str(ROOT/'tools/run_complete.py'),'--manifest',str(manifest),'--reuse-evaluation',str(prior),'--output',str(output)],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
    full=json.loads((output/'complete-report.json').read_text())
    assert full['reusedInference'] is True
    assert full['cases'][0]['comparison']['document_validity']=='NOT_VERIFIED'
    page=(output/'review.html').read_text()
    assert '&lt;unsafe&gt;' in page and '<unsafe>' not in page
    assert (output/'summary.csv').exists()
    assert not (output/'evaluation/reader-runs').exists()
    again=subprocess.run([sys.executable,str(ROOT/'tools/run_complete.py'),'--manifest',str(manifest),'--reuse-evaluation',str(prior),'--output',str(output)],capture_output=True)
    assert again.returncode!=0


def test_hash_mismatch_rejected_before_using_coordinates(tmp_path):
    spec=importlib.util.spec_from_file_location('diagnose_batch_test',ROOT/'tools/diagnose_batch.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    path=tmp_path/'image.jpg';path.write_bytes(b'not-source')
    import pytest
    with pytest.raises(ValueError,match='SOURCE_HASH_MISMATCH'):
        module.bound_image(path,{'images':[{'side':'FRONT','sha256':'0'*64}]},'FRONT')
