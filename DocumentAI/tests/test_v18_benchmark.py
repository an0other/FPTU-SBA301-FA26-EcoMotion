import importlib.util
import json
from pathlib import Path


def test_benchmark_reuses_reader_but_performs_fresh_inference(tmp_path,monkeypatch):
    import document_ai.reader.factory as factory
    class Ocr:
        closed=False
        def close(self):self.closed=True
    class Reader:
        count=0
        ocr=Ocr()
        def read(self,*args):
            self.count+=1
            return {'status':'REVIEW_REQUIRED','elapsedMs':10,'data':{'fullName':'NGUYEN AN'},'checks':[]}
    reader=Reader()
    monkeypatch.setattr(factory,'configured_reader',lambda:reader)
    front=tmp_path/'f';back=tmp_path/'b';front.write_bytes(b'front');back.write_bytes(b'back')
    tool=Path(__file__).resolve().parents[1]/'tools/benchmark_reader_latency.py'
    spec=importlib.util.spec_from_file_location('latency_tool',tool)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    monkeypatch.setattr(module.sys,'argv',['benchmark','--front',str(front),'--back',str(back),
        '--corner-weights','unused','--vietocr-python','unused','--output',str(tmp_path/'out'),'--repeats','3'])
    # Prevent test environment variables from leaking to later tests.
    monkeypatch.setattr(module.os,'environ',dict(module.os.environ))
    assert module.main()==0
    report=json.loads((tmp_path/'out/benchmark.json').read_text())
    assert reader.count==3 and reader.ocr.closed
    assert report['summary']['warmCount']==2
    assert all(not row['dataDifferences'] for row in report['runs'])
