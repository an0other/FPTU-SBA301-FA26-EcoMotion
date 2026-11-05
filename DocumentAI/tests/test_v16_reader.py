import io
import json
import sys
from PIL import Image
from document_ai.reader.vietocr_pipeline import VietOcrPipeline,VietOcrProcess
from document_ai.reader.data import fuse,TYPES
from document_ai.reader.service import Reader


def line(text):
    return {'text':text,'confidence':.9,'polygon':[[1,1],[150,1],[150,30],[1,30]]}


def test_secondary_is_used_and_primary_preserved_without_qr():
    class Primary:
        def extract(self,image):return [line('VU TIEN DON')]
    class Recognizer:
        model='vgg_seq2seq'
        def recognize_many(self,images):
            assert len(images)==1
            return [{'text':'VŨ TIẾN ĐƠN','confidence':.95}]
    engine=VietOcrPipeline(Primary(),Recognizer())
    result=engine.extract(Image.new('RGB',(200,50)))
    assert result[0]['text']=='VŨ TIẾN ĐƠN'
    assert result[0]['primaryText']=='VU TIEN DON'
    fields,_=fuse({'fullName':{'candidate':{'value':result[0]['text'],
        'confidence':.95,'recognitionEngine':result[0]['recognitionEngine']}}},{},TYPES[0])
    assert fields['fullName']['value']=='VŨ TIẾN ĐƠN'
    assert fields['fullName']['source']=='OCR'
    assert fields['fullName']['status']=='OCR_ONLY'


def test_worker_is_persistent_and_uses_utf8_protocol(tmp_path,monkeypatch):
    import document_ai.reader.vietocr_pipeline as module
    worker=tmp_path/'fake_worker.py'
    worker.write_text('import sys,json\nfor line in sys.stdin:\n r=json.loads(line)\n print(json.dumps({"id":r["id"],"status":"OK","results":[{"text":"NGUYỄN AN","confidence":.99} for _ in r["images"]]}),flush=True)\n',encoding='utf-8')
    original=module.subprocess.Popen
    monkeypatch.setattr(module.subprocess,'Popen',lambda args,**kwargs:original([sys.executable,'-u',str(worker)],**kwargs))
    process=VietOcrProcess(sys.executable,log=tmp_path/'log')
    try:
        assert process.recognize_many([Image.new('RGB',(30,20))])[0]['text']=='NGUYỄN AN'
        pid=process.process.pid
        assert process.recognize_many([Image.new('RGB',(30,20))])[0]['text']=='NGUYỄN AN'
        assert process.process.pid==pid
    finally:process.close()


def test_qr_disabled_does_not_call_scanner_and_no_qr_name_substitution(monkeypatch):
    import document_ai.reader.service as service
    calls=[]
    class Primary:
        def extract(self,image):return []
    class Engine:
        orientation_engine=Primary();implementation='vietocr-test'
        def recognize_detected(self,image,lines):
            return [{**v,'recognitionEngine':'vietocr-vgg_seq2seq'} for v in lines]
    def orientation(image,ocr,card_rectified):
        calls.append(ocr)
        texts=["GIAY PHEP LAI XE/DRIVER'S LICENSE",'Số/No: 790000000001',
               'Full name: NGUYỄN AN','Date of birth: 02/01/2000','Class: A1']
        return {'status':'RESOLVED','image':image,'lines':[line(t) for t in texts] if len(calls)==1 else [],'rotationApplied':0,'attempts':[]}
    monkeypatch.setattr(service,'resolve_orientation',orientation)
    def scanner(*args,**kwargs):raise AssertionError('QR must not run')
    def raw(color):
        stream=io.BytesIO();Image.new('RGB',(800,500),color).save(stream,format='PNG');return stream.getvalue()
    engine=Engine()
    reader=Reader(ocr=engine,locator=lambda im,h:{'status':'LOCATED','image':im},scanner=scanner,
        quality_config={'min_short_side':1,'min_laplacian_variance':0,'max_dark_fraction':1,'max_bright_fraction':1},qr_enabled=False)
    result=reader.read(raw('white'),raw('gray'),TYPES[0])
    assert calls==[engine.orientation_engine,engine.orientation_engine]
    assert result['readerConfig']['qrEnabled'] is False
    assert all(v['status']=='SKIPPED' for v in result['checks'] if v['step']=='QR')
    assert result['qr']['parsedSchemas']==[]
    assert result['fields']['fullName']['source']=='OCR'
    assert result['data']['fullName']=='NGUYỄN AN'
    assert result['ocrData']['fullName']['candidate']['value']=='NGUYỄN AN'
    assert result['coreComparison']['status']=='PARTIAL'


def test_secondary_failure_never_silently_uses_primary():
    import pytest
    class Primary:
        def extract(self,image):return [line('NGUYEN AN')]
    class Bad:
        model='vgg_seq2seq'
        def recognize_many(self,images):raise RuntimeError('worker failed')
    with pytest.raises(RuntimeError):VietOcrPipeline(Primary(),Bad()).extract(Image.new('RGB',(200,50)))
