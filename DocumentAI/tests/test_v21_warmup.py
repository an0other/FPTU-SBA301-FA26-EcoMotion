from PIL import Image
import pytest
from fastapi.testclient import TestClient
from document_ai.reader.api import create_app


def paths(tmp_path):
    a=tmp_path/'front.png';b=tmp_path/'back.png'
    Image.new('RGB',(20,20),'white').save(a);Image.new('RGB',(20,20),'gray').save(b)
    return a,b


class Ocr:
    closed=False
    def close(self):self.closed=True


class FakeReader:
    def __init__(self,status='READ_SUCCESS'):
        self.calls=0;self.ocr=Ocr();self.status=status
    def read(self,front,back,kind):
        self.calls+=1
        return {'status':self.status,'checks':[{'side':side,'step':'OCR','status':'PASS','metrics':{'lineCount':3}} for side in ('FRONT','BACK')]}


def test_startup_warmup_precedes_ready_health_and_shutdown_closes_worker(tmp_path):
    reader=FakeReader()
    with TestClient(create_app(reader,warmup_files=paths(tmp_path),api_key='test-api-key-for-document-ai-tests-0001'),
                    headers={'X-API-Key':'test-api-key-for-document-ai-tests-0001'}) as client:
        assert reader.calls==1
        health=client.get('/health').json()
        assert health['modelInferenceChecked'] is True
        assert health['warmup']['state']=='COMPLETED'
        assert 'data' not in health
    assert reader.ocr.closed


def test_warmup_bad_input_does_not_claim_ready(tmp_path):
    reader=FakeReader('RETAKE_REQUIRED')
    with pytest.raises(RuntimeError,match='Warm-up'):
        with TestClient(create_app(reader,warmup_files=paths(tmp_path),api_key='test-api-key-for-document-ai-tests-0001')):pass
    assert reader.ocr.closed


def test_without_warmup_health_does_not_claim_model_loaded():
    reader=FakeReader()
    with TestClient(create_app(reader,warmup_files=(),api_key='test-api-key-for-document-ai-tests-0001'),
                    headers={'X-API-Key':'test-api-key-for-document-ai-tests-0001'}) as client:
        health=client.get('/health').json()
        assert reader.calls==0
        assert health['modelInferenceChecked'] is False
        assert health['warmup']['state']=='NOT_RUN'


def test_gpu_benchmark_device_is_recorded_and_forwarded(tmp_path,monkeypatch):
    import importlib.util
    from pathlib import Path
    import document_ai.reader.factory as factory
    import os
    import json
    reader=FakeReader()
    def read(*args):
        reader.calls+=1
        assert os.environ['DOCUMENT_AI_RECOGNIZER_DEVICE']=='cuda:0'
        return {'status':'READ_SUCCESS','elapsedMs':1,'data':{},'checks':[]}
    reader.read=read
    monkeypatch.setattr(factory,'configured_reader',lambda:reader)
    p=Path(__file__).resolve().parents[1]/'tools/benchmark_reader_latency.py'
    spec=importlib.util.spec_from_file_location('gpu_bench_test',p)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    a,b=paths(tmp_path)
    monkeypatch.setattr(module.sys,'argv',['bench','--front',str(a),'--back',str(b),'--corner-weights','unused',
        '--vietocr-python','unused','--recognizer-device','cuda:0','--repeats','2','--output',str(tmp_path/'out')])
    monkeypatch.setattr(module.os,'environ',dict(os.environ))
    assert module.main()==0
    assert json.loads((tmp_path/'out/benchmark.json').read_text())['recognizerDevice']=='cuda:0'
