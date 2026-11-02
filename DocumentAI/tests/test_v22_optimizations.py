import base64
import importlib.util
import json
from pathlib import Path
import pytest
from PIL import Image
from document_ai.adapters.ocr import VietOcrRecognizer
from document_ai.reader.crop_transport import decode_crops
from document_ai.reader.field_scope import select_field_regions
from document_ai.core import StepOutput, Status


def test_batch_preserves_original_order_and_uses_no_padding():
    class Predictor:
        calls=[]
        def predict_batch(self, images, return_prob):
            self.calls.append([i.size for i in images])
            return [str(i.width) for i in images], [.95]*len(images)
    r=VietOcrRecognizer();r._predictor=Predictor()
    images=[Image.new('RGB',(w,10)) for w in (90,20,60)]
    rows=r.recognize_batch(images,2)
    assert [v['text'] for v in rows]==['90','20','60']
    assert r._predictor.calls==[[(20,10),(60,10)],[(90,10)]]
    assert all(v['elapsedMs'] is None for v in rows)


@pytest.mark.parametrize('scores', [[float('nan')],[1.1],[]])
def test_batch_invalid_results_fail_explicitly(scores):
    class Predictor:
        def predict_batch(self,images,return_prob):return ['text'],scores
    r=VietOcrRecognizer();r._predictor=Predictor()
    with pytest.raises(ValueError):r.recognize_batch([Image.new('RGB',(20,10))],4)


def test_lossless_raw_transport_and_png_have_same_pixels(tmp_path):
    image=Image.new('RGB',(30,12),(18,76,231));image.putpixel((3,3),(255,0,11))
    path=tmp_path/'crop.png';image.save(path)
    raw={'width':30,'height':12,'rgb':base64.b64encode(image.tobytes()).decode()}
    a,b=decode_crops([raw,str(path)])
    assert a.size==image.size and a.tobytes()==b.tobytes()==image.tobytes()


@pytest.mark.parametrize('w,h,payload',[(-2,-3,''),(True,10,''),(10,10,'eA=='),(1,1,'!@#$')])
def test_raw_invalid_shapes_and_bytes_are_rejected(w,h,payload):
    with pytest.raises(ValueError):decode_crops([{'width':w,'height':h,'rgb':payload}])


def test_field_scope_is_opt_in_and_unknown_type_uses_full(tmp_path):
    image=Image.new('RGB',(1000,600))
    regions=[{'polygon':[[0,y],[100,y],[100,y+20],[0,y+20]]} for y in (10,300,500)]
    profile=tmp_path/'p.json';profile.write_text(json.dumps({'documentType':'VN_DRIVING_LICENSE','contextMarginFraction':0,'fields':{}}))
    values,report=select_field_regions(regions,image,profile,'FRONT','VN_CITIZEN_ID',[])
    assert values==regions and report['status']=='FULL'
    values,report=select_field_regions(regions,image,profile,'FRONT','VN_DRIVING_LICENSE',[])
    assert values==regions and report['reason']=='LAYOUT_CLUE_NOT_CONFIRMED'
    values,report=select_field_regions(regions,image,profile,'FRONT','VN_DRIVING_LICENSE',[{'text':'GIẤY PHÉP LÁI XE'}])
    assert values==regions[:1] and len(report['skippedRegions'])==2
    assert report['validated'] is False


def test_adaptive_qr_success_marks_limited_search(monkeypatch):
    from test_v17_contract import fake_reader,raw
    r=fake_reader(monkeypatch);r.qr_search='adaptive'
    calls=[];original=r.scanner
    def scanner(image,config,**kwargs):
        calls.append(config);return original(image,config,**kwargs)
    r.scanner=scanner
    result=r.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert len(calls)==4 and all(c['max_attempts']==2 for c in calls)
    assert result['qrData']['searchCoverage']=='QUICK_ONLY'
    assert 'QR_SEARCH_LIMITED_TO_QUICK_PASSES' in result['warnings']
    assert result['verification']['authenticity']=='NOT_VERIFIED'


@pytest.mark.parametrize('quick_error',[False,True])
def test_adaptive_no_quick_payload_or_error_retries_full(monkeypatch,quick_error):
    from test_v17_contract import fake_reader,raw,QR
    r=fake_reader(monkeypatch);r.qr_search='adaptive';calls=[]
    def scanner(image,config,**kwargs):
        calls.append(config)
        if config.get('max_attempts')==2:
            return StepOutput(Status.ERROR if quick_error else Status.INCONCLUSIVE,payload={'values':[]})
        return StepOutput(Status.PASS,payload={'values':[QR]})
    r.scanner=scanner
    result=r.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert len(calls)==8 and result['qrData']['searchCoverage']=='FULL_STRATEGY'
    assert result['qrData']['fields']['licenseClass']=='A1'
    assert result['status']!='PROCESSING_ERROR'


def test_adaptive_conflicts_are_not_discarded_by_full_retry(monkeypatch):
    from test_v17_contract import fake_reader,raw,QR
    r=fake_reader(monkeypatch);r.qr_search='adaptive';count=0
    def scanner(image,config,**kwargs):
        nonlocal count
        count+=1
        value=QR.replace('790000000001','790000000002') if count==2 else QR
        return StepOutput(Status.PASS,payload={'values':[value]})
    r.scanner=scanner
    result=r.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert result['status']=='INCONSISTENT_DATA'
    assert result['qrData']['status']=='CONFLICT'
    assert len({d['rawPayload'] for d in result['qrData']['documents']})==2


def test_matrix_compares_actual_ocr_even_if_qr_final_values_match():
    tool=Path(__file__).resolve().parents[1]/'tools/test_optimizations.py'
    spec=importlib.util.spec_from_file_location('matrix_tool',tool)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    baseline={'data':{'fullName':'Nguyễn An'},'ocrData':{'fullName':{'candidate':{'value':'Nguyễn An'}}}}
    actual={'data':{'fullName':'Nguyễn An'},'ocrData':{'fullName':{'candidate':{'value':'Nguyen An'}}}}
    changes=module.differences(baseline,actual)
    assert 'ocrCandidates' in changes and 'data' not in changes


def test_missing_core_field_in_limited_read_retries_full_ocr_before_qr_fusion(monkeypatch,tmp_path):
    from test_v17_contract import fake_reader,raw
    import document_ai.reader.service as service
    reader=fake_reader(monkeypatch)
    original=service.resolve_orientation
    monkeypatch.setattr(service,'resolve_orientation',lambda image,ocr,**kwargs:original(image,ocr,kwargs['card_rectified']))
    class Ocr:
        implementation='test-vietocr'
        rescued=0
        full=[]
        def recognize_detected(self,*args):raise AssertionError('Use read_oriented')
        def read_oriented(self,image,orientation_lines=None,**kwargs):
            if orientation_lines:
                self.full=orientation_lines
                if kwargs.get('side')=='FRONT':
                    self.last_scope={'status':'APPLIED'}
                    return [line for line in orientation_lines if not line['text'].startswith('Full name:')]
            if kwargs.get('side')=='BACK':self.last_scope={'status':'FULL'};return []
            self.rescued+=1
            return self.full
    profile=tmp_path/'p.json';profile.write_text(json.dumps({'id':'test','documentType':'VN_DRIVING_LICENSE','contextMarginFraction':0,'fields':{}}))
    reader.ocr=Ocr();reader.field_profile=profile;reader.recognition_scope='fields';reader.performance_mode='optimized'
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert reader.ocr.rescued==1
    assert result['ocrData']['fullName']['candidate']['value']=='NGUYỄN AN'
    assert result['recognitionScopeReport'][0]['fallback']=='FULL_CORE_FIELD_NOT_READ'
    assert any(c['step']=='FIELD_SCOPE_FALLBACK' and c['status']=='COMPLETED' for c in result['checks'])


def test_matrix_keeps_successful_cases_after_one_child_fails(tmp_path,monkeypatch):
    tool=Path(__file__).resolve().parents[1]/'tools/test_optimizations.py'
    spec=importlib.util.spec_from_file_location('matrix_run',tool)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    paths=[]
    for name in ('front','back','weights','interpreter','profile'):
        path=tmp_path/name;path.write_text('test');paths.append(str(path))
    commands=[]
    class Child:
        stdout=['test benchmark\n']
        def __init__(self,command,**kwargs):
            commands.append(command);self.code=1 if len(commands)==2 else 0
            folder=Path(command[command.index('--output')+1]);folder.mkdir()
            if self.code:return
            response={'data':{'fullName':'Nguyễn An'},'status':'READ_SUCCESS'}
            for i in (1,2,3):(folder/f'response-{i:02}.json').write_text(json.dumps(response))
            (folder/'benchmark.json').write_text(json.dumps({'summary':{'firstRequestMs':10,'warmMedianMs':5,'processingErrors':0},'runs':[{'iteration':i} for i in (1,2,3)]}))
        def __enter__(self):return self
        def __exit__(self,*args):pass
        def wait(self):return self.code
    monkeypatch.setattr(module.subprocess,'Popen',Child)
    monkeypatch.setattr(module.sys,'argv',['matrix','--front',paths[0],'--back',paths[1],
        '--corner-weights',paths[2],'--vietocr-python',paths[3],'--profile',paths[4],
        '--output',str(tmp_path/'out')])
    assert module.main()==1
    report=json.loads((tmp_path/'out/matrix.json').read_text())
    assert len(commands)==3 and len(report['cases'])==3
    assert report['cases'][1]['exitCode']==1
    assert report['cases'][2]['comparisonAvailable'] is True
    assert report['cases'][2]['changedRuns']==0
    assert commands[0][commands[0].index('--batch-size')+1]=='1'
    assert commands[2][commands[2].index('--qr-search')+1]=='adaptive'
