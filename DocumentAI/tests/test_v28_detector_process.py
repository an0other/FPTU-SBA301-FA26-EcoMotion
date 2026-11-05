import json,sys,time
from types import SimpleNamespace
from pathlib import Path
import pytest
from PIL import Image
from document_ai.reader.detector_process import DetectorProcess,DetectorTimeout,DetectorFailure


def worker(tmp_path,mode='normal'):
    script=tmp_path/'fake_detector.py'
    script.write_text('''import json,sys,time,os
MODE=MODE_LITERAL
if MODE=='startuphang':time.sleep(5)
print(json.dumps({'type':'READY','startupMs':1}),flush=True)
if MODE=='notreading':time.sleep(5)
for line in sys.stdin:
 r=json.loads(line);key=r['id']
 print(json.dumps({'type':'STAGE','id':key,'stage':'PREDICT_AND_NATIVE_POSTPROCESS'}),flush=True)
 if r['width']==80:time.sleep(5)
 if r['width']==82:os._exit(4)
 print(json.dumps({'type':'RESULT','id':999 if r['width']==83 else key,'regions':[], 'timing':{'inputSize':[r['width'],r['height']]}}),flush=True)
'''.replace('MODE_LITERAL',repr(mode)))
    return script


def detector(tmp_path,mode='normal'):
    return DetectorProcess(python=sys.executable,timeout=.15,startup_timeout=3,worker_script=worker(tmp_path,mode),log=tmp_path/'worker.log')


def test_timeout_kills_process_and_does_not_retry_in_same_request(tmp_path):
    d=detector(tmp_path);began=time.perf_counter()
    try:
        with pytest.raises(DetectorTimeout) as caught:d.detect(Image.new('RGB',(80,40)))
        assert time.perf_counter()-began<2 and d.process is None
        assert caught.value.metadata['lastWorkerStage']=='PREDICT_AND_NATIVE_POSTPROCESS'
        with pytest.raises(DetectorFailure):d.detect(Image.new('RGB',(81,40)))
        assert d.process is None
        d.begin_request();d.detect(Image.new('RGB',(81,40)))
        assert d.is_ready and d.last_timing['coldProcess'] is True
        pid=d.process.pid;d.detect(Image.new('RGB',(81,40)))
        assert d.process.pid==pid and d.last_timing['coldProcess'] is False
    finally:d.close()


def test_startup_wait_has_its_own_deadline(tmp_path):
    d=DetectorProcess(python=sys.executable,timeout=.15,startup_timeout=.15,worker_script=worker(tmp_path,'startuphang'))
    try:
        with pytest.raises(DetectorTimeout) as caught:d.detect(Image.new('RGB',(81,40)))
        assert caught.value.metadata['phase']=='STARTUP' and d.process is None
    finally:d.close()


def test_timeout_also_covers_blocked_stdin_write(tmp_path):
    d=detector(tmp_path,'notreading');began=time.perf_counter()
    try:
        with pytest.raises(DetectorTimeout):d.detect(Image.new('RGB',(1200,1200)))
        assert d.process is None and time.perf_counter()-began<2
    finally:d.close()


@pytest.mark.parametrize('width,reason',[(82,'DETECTOR_WORKER_EXITED'),(83,'DETECTOR_PROTOCOL_ERROR')])
def test_worker_crash_and_wrong_reply_are_explicit_errors(tmp_path,width,reason):
    d=detector(tmp_path)
    try:
        with pytest.raises(DetectorFailure) as caught:d.detect(Image.new('RGB',(width,40)))
        assert caught.value.reason==reason and d.process is None
    finally:d.close()


def test_detector_failure_is_not_swallowed_by_orientation_probe_loop():
    from document_ai.reader.fast_orientation import orient
    calls=[]
    def extract(image):calls.append(1);raise DetectorTimeout('DETECTOR_TIMEOUT',{'phase':'INFERENCE'})
    with pytest.raises(DetectorTimeout):orient(Image.new('RGB',(800,500)),SimpleNamespace(extract=extract),None)
    assert len(calls)==1


def test_staged_reader_reports_processing_error_after_detector_timeout(monkeypatch):
    from test_v25_staged import staged
    from test_v17_contract import raw
    import document_ai.reader.staged as module
    reader=staged(monkeypatch)
    def fail(*args,**kwargs):raise DetectorTimeout('DETECTOR_TIMEOUT',{'phase':'INFERENCE','timeoutSeconds':8})
    monkeypatch.setattr(module,'orient',fail)
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert result['status']=='PROCESSING_ERROR' and 'DETECTOR_TIMEOUT' in result['statusReasons']
    assert result['executionReport']['exitStage']=='DETECTOR_FAILURE'
    assert not any(c['step']=='OCR' and c['status']=='PASS' for c in result['checks'])


def test_public_detector_timing_does_not_label_native_postprocess_as_pure_forward():
    from document_ai.reader.text_regions import PaddleTextRegions
    class Model:
        def predict(self,**kwargs):return [SimpleNamespace(json={'res':{'dt_polys':[],'dt_scores':[]}})]
    d=PaddleTextRegions(model=Model());events=[];d.detect(Image.new('RGB',(200,100)),progress=events.append)
    assert d.last_timing['inputSize']==[200,100]
    assert 'predictAndNativePostprocessMs' in d.last_timing and 'parseAndValidationMs' in d.last_timing
    assert events==['PREPARING_INPUT','PREDICT_AND_NATIVE_POSTPROCESS','PARSING_AND_VALIDATING_OUTPUT']


def test_close_releases_detector_when_recognizer_close_fails():
    from document_ai.reader.vietocr_pipeline import DetectorOnlyVietOcrPipeline
    class Recognizer:
        model='vgg_transformer'
        def close(self):raise RuntimeError('close failed')
    closed=[]
    d=SimpleNamespace(close=lambda:closed.append(True))
    with pytest.raises(RuntimeError):DetectorOnlyVietOcrPipeline(object(),Recognizer(),d).close()
    assert closed==[True]


@pytest.mark.parametrize('focus',[False,True])
def test_validation_counts_slow_complete_cases_as_failed_latency(tmp_path,monkeypatch,focus):
    import importlib.util,itertools
    import document_ai.reader.factory as factory
    path=Path(__file__).resolve().parents[1]/'tools/test_v30.py'
    spec=importlib.util.spec_from_file_location('v28_check',path);tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
    files=[]
    for i in range(4):
        p=tmp_path/f'{i}.jpg';Image.new('RGB',(40,30),(i*40,0,0)).save(p);files.append(str(p))
    weights=tmp_path/'m.pt';weights.write_bytes(b'm');weights.with_suffix('.card.json').write_text('{}')
    class Reader:
        qr_enabled=True
        ocr=SimpleNamespace(close=lambda:None)
        def read(self,front,back,kind):
            negative=kind=='AUTO' or front==Path(files[2]).read_bytes() and kind=='VN_DRIVING_LICENSE'
            if negative:return {'status':'RETAKE_REQUIRED'}
            keys=['documentNumber','fullName','dateOfBirth']+(['sex'] if kind=='VN_CITIZEN_ID' else ['licenseClass'])
            return {'status':'READ_SUCCESS','documentType':kind,'executionReport':{'earlyExit':False},
                'checks':[{'side':s,'step':'OCR','status':'PASS','metrics':{'lineCount':1}} for s in ['FRONT','BACK']],
                'ocrData':{k:{'status':'EXTRACTED','candidate':{'value':'test'}} for k in keys}}
    monkeypatch.setattr(factory,'configured_reader',lambda:Reader())
    clock=itertools.count(step=.01);monkeypatch.setattr(tool,'time',SimpleNamespace(perf_counter=lambda:next(clock)))
    args=['check','--gplx-front',files[0],'--gplx-back',files[1],'--cccd-front',files[2],'--cccd-back',files[3],
        '--corner-weights',str(weights),'--vietocr-python',sys.executable,'--output',str(tmp_path/'out'),
        '--repeats','2','--target-ms','1']
    args+=['--focus-cccd90'] if focus else ['--include-no-qr','--include-rotations']
    monkeypatch.setattr(sys,'argv',args)
    assert tool.main()==1
    report=json.loads((tmp_path/'out/summary.json').read_text())
    phases={r['phase'] for r in report['latencyViolations']}
    assert phases==({'warm'} if focus else {'warm','no-qr','rotations'})
    assert all(r['latencyTargetMet'] is None for r in report['cases'] if r['phase']=='startup')
