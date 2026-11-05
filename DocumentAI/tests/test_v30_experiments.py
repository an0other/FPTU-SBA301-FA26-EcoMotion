import json
from pathlib import Path
from types import SimpleNamespace
import numpy as np
import pytest
from PIL import Image
from document_ai.reader.field_regions import observe_profile,region_extract,boxes
from document_ai.reader.image_preprocessing import Normalizer
from document_ai.reader.experiments import environment


def field_line(text,y=130):
    return {'text':text,'confidence':.97,'side':'FRONT','polygon':[[210,y],[350,y],[350,y+10],[210,y+10]],'recognitionEngine':'test'}


@pytest.fixture
def profile(tmp_path):
    p=tmp_path/'profile.json';p.write_text(json.dumps({'id':'test','documentType':'VN_DRIVING_LICENSE','contextMarginFraction':0,
        'fields':{'documentNumber':[.5,.1,.95,.3],'fullName':[.5,.3,.95,.45],'dateOfBirth':[.5,.45,.95,.63],'licenseClass':[.5,.65,.95,.85]}}))
    return p


def test_fallback_and_final_profile_agree_without_date_label(profile):
    lines=[field_line('790000000001',40),field_line('NGUYỄN AN',85),field_line('23/12/2004'),field_line('A1',180)];image=Image.new('RGB',(400,250))
    early=observe_profile(lines,'VN_DRIVING_LICENSE',image,profile)
    no_inference=SimpleNamespace(extract=lambda im:pytest.fail('No OCR should be needed'))
    final=region_extract(lines,'VN_DRIVING_LICENSE',image,profile,no_inference,retry_policy='direct_first')
    assert early['dateOfBirth']['candidate']['value']=='2004-12-23'
    assert early['dateOfBirth']['candidate']['value']==final['dateOfBirth']['candidate']['value']


def test_profile_observer_keeps_date_conflict(profile):
    rows=[field_line('23/12/2004'),field_line('24/12/2004',145)]
    result=observe_profile(rows,'VN_DRIVING_LICENSE',Image.new('RGB',(400,250)),profile)
    assert result['dateOfBirth']['status']=='AMBIGUOUS' and result['dateOfBirth']['candidate'] is None
    assert len(result['dateOfBirth']['candidates'])==2


def test_profile_observer_does_not_accept_wrong_type_or_failed_text(profile):
    with pytest.raises(ValueError):observe_profile([],'VN_CITIZEN_ID',Image.new('RGB',(400,250)),profile)
    row={**field_line('23/12/2004'),'recognition_status':'ERROR'}
    assert observe_profile([row],'VN_DRIVING_LICENSE',Image.new('RGB',(400,250)),profile)['dateOfBirth']['candidate'] is None


@pytest.mark.parametrize('policy,expected_calls',[('profile',2),('v29',3)])
def test_staged_profile_guard_avoids_spurious_full_reread(monkeypatch,profile,policy,expected_calls):
    from test_v25_staged import staged,line
    from test_v17_contract import raw
    import document_ai.reader.staged as module
    reader=staged(monkeypatch);reader.qr_enabled=False;reader.field_profile=profile;reader.recognition_scope='fields'
    monkeypatch.setenv('DOCUMENT_AI_FALLBACK_POLICY',policy)
    front_rows=[line('GIAY PHEP LAI XE',5),field_line('790000000001',40),field_line('NGUYỄN AN',85),field_line('23/12/2004'),field_line('A1',180)]
    def orient(image,*args,**kwargs):
        front=image.getpixel((0,0))==(255,255,255)
        return {'status':'RESOLVED','rotationApplied':0,'image':image.resize((400,250)),
            'lines':front_rows if front else [line('CLASSIFICATION OF MOTOR VEHICLES')],'attempts':[],'reason':None}
    monkeypatch.setattr(module,'orient',orient)
    calls=[]
    def read(image,lines,**options):
        calls.append(options);is_front=any(l.get('text')=='GIAY PHEP LAI XE' for l in lines)
        reader.ocr.last_scope={'status':'APPLIED' if is_front else 'FULL','orientationGeometry':{'status':'REUSED' if is_front else 'NOT_REUSED'}}
        return lines
    reader.ocr.read_oriented=read
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert len(calls)==expected_calls
    assert result['ocrData']['dateOfBirth']['candidate']['value']=='2004-12-23'


def test_numpy_normalization_matches_upstream_without_mean_std():
    pixels=np.random.default_rng(7).integers(0,256,(3,32,80,3),dtype=np.uint8)
    expected=np.asarray(pixels.transpose(0,3,1,2)/255,dtype=np.float32)
    actual=Normalizer().prepare(pixels)
    assert np.array_equal(actual,expected) and actual.flags.c_contiguous
    assert np.array_equal(Normalizer().prepare(np.zeros((1,1,1,3),dtype=np.uint8)),np.zeros((1,3,1,1),dtype=np.float32))


@pytest.mark.parametrize('pixels',[np.zeros((2,3,3),dtype=np.uint8),np.zeros((1,32,10,3),dtype=np.float32),np.zeros((33,32,10,3),dtype=np.uint8)])
def test_preprocessor_rejects_invalid_buffers(pixels):
    with pytest.raises(ValueError):Normalizer().prepare(pixels)


def test_direct_field_geometry_replaces_only_profile_values(profile):
    from document_ai.reader.direct_fields import profile_regions
    image=Image.new('RGB',(400,250));header=field_line('title',2);value=field_line('birth',130)
    regions,report=profile_regions(image,[header,value],profile)
    assert header in regions and value not in regions and report['replacedDetectedRegions']==1
    assert any(r.get('fieldHint')=='dateOfBirth' for r in regions)


def test_experiments_are_independent_and_use_numpy_preprocessing():
    assert environment('fixed')['DOCUMENT_AI_BATCH_POLICY']=='legacy'
    assert environment('device')['DOCUMENT_AI_DECODE_BACKEND']=='device'
    assert environment('bucket')['DOCUMENT_AI_FIELD_READ_MODE']=='detected'
    assert environment('direct')['DOCUMENT_AI_BATCH_POLICY']=='legacy'
    assert environment('combined')['DOCUMENT_AI_PREPROCESSOR']=='numpy'


def reference_translate(img,model,max_steps=8):
    # Reference semantics of upstream 0.3.13, including confidence after row EOS.
    import torch
    model.eval();memory=model.transformer.forward_encoder(model.cnn(img))
    history=[[1]*len(img)];ps=[[1]*len(img)]
    with torch.no_grad():
        while len(history)-1<=max_steps and not all(np.any(np.array(history).T==2,axis=1)):
            output,memory=model.transformer.forward_decoder(torch.LongTensor(history),memory)
            probabilities=torch.softmax(output,dim=-1).cpu()
            values,ids=torch.topk(probabilities,5,dim=-1)
            history.append(ids[:,-1,0].tolist());ps.append(values[:,-1,0].tolist())
    ids=np.array(history).T;scores=np.array(ps).T*(ids>3)
    return ids,scores.sum(-1)/(scores>0).sum(-1)


def toy_model(torch,finish=(2,4)):
    class Transformer:
        def forward_encoder(self,src):return src
        def forward_decoder(self,history,memory):
            b=history.shape[1];length=history.shape[0]
            logits=torch.zeros((b,length,8),device=history.device)
            for i in range(b):
                for step in range(length):
                    token=2 if step+1==finish[i] else 4+i
                    logits[i,step,token]=3.+step*.1
            return logits,memory
    return SimpleNamespace(cnn=lambda im:im,transformer=Transformer(),eval=lambda:None)


def test_device_decoder_matches_reference_tokens_and_confidence_on_cpu():
    torch=pytest.importorskip('torch')
    from document_ai.reader.experimental_decode import device_translate
    model=toy_model(torch);img=torch.zeros((2,3,32,80))
    old,old_scores=reference_translate(img,model)
    new,new_scores,report=device_translate(img,model,max_seq_length=8)
    assert np.array_equal(old,new) and np.allclose(old_scores,new_scores,atol=1e-7)
    assert report['fullDistributionCpuCopiesPerStep']==0 and report['steps']==4


def test_decoder_no_eos_is_truncated_not_success():
    torch=pytest.importorskip('torch')
    from document_ai.reader.experimental_decode import device_translate
    model=toy_model(torch,finish=(10,10));img=torch.zeros((2,3,32,80))
    ids,scores,report=device_translate(img,model,max_seq_length=1)
    assert np.isnan(scores).all() and report['truncatedIndexes']==[0,1] and report['steps']==2


def test_exact_and_bucket_batching_report_actual_shapes_and_preserve_order():
    torch=pytest.importorskip('torch')
    from document_ai.reader.experimental_predictor import ExperimentalPredictor
    from document_ai.reader.experimental_predictor import resized_width
    assert resized_width(100,32,{'image_height':32,'image_min_width':30,'image_max_width':512})==100
    class Model:
        def eval(self):pass
        def cnn(self,img):return img
        transformer=toy_model(torch,finish=(2,2)).transformer
    base=SimpleNamespace(config={'dataset':{'image_height':32,'image_min_width':30,'image_max_width':512}},device='cpu',model=Model(),vocab=SimpleNamespace(batch_decode=lambda ids:[str(r[1]) for r in ids]))
    images=[Image.new('RGB',(80,32)),Image.new('RGB',(100,32))]
    predictor=ExperimentalPredictor(base,policy='bucket',decoder='device',bucket_width=64)
    texts,scores=predictor.predict_batch(images,True)
    assert texts==['4','5'] and predictor.last_report['actualBatchSizes']==[2]
    assert predictor.last_report['groups'][0]['tensorWidth']==128
    with pytest.raises(ValueError):ExperimentalPredictor(base,precision='fp16')


def test_encoder_hook_reports_real_microbatches():
    torch=pytest.importorskip('torch')
    from document_ai.adapters.ocr import VietOcrRecognizer
    class Predictor:
        model=SimpleNamespace(cnn=torch.nn.Identity())
        def predict_batch(self,images,return_prob):
            # Deliberately simulate two exact-width sub-buckets in one outer chunk.
            self.model.cnn(torch.zeros((1,3,32,80)));self.model.cnn(torch.zeros((1,3,32,100)))
            return ['A','B'],[.98,.99]
    r=VietOcrRecognizer();r._predictor=Predictor()
    rows=r.recognize_batch([Image.new('RGB',(80,32)),Image.new('RGB',(100,32))],4)
    group=r.last_batch_groups[0]
    assert group['actualForwardCount']==2 and [x['batchSize'] for x in group['observedEncoderForwards']]==[1,1]
    assert len(Predictor.model.cnn._forward_pre_hooks)==0 and len(rows)==2


def test_truncated_decode_cannot_be_promoted_by_single_crop_numeric_retry():
    from document_ai.adapters.ocr import VietOcrRecognizer
    class Predictor:
        calls=0;last_report={'truncatedImageIndexes':[0]}
        def predict_batch(self,images,return_prob):return ['UNFINISHED'],[float('nan')]
        def predict(self,image,return_prob):self.calls+=1;return 'WOULD_HIDE_TRUNCATION',.99
    r=VietOcrRecognizer();r._predictor=Predictor()
    row=r.recognize_batch([Image.new('RGB',(80,32))],4,tolerate_invalid=True)[0]
    assert row['status']=='ERROR' and row['errorType']=='DECODE_TRUNCATED' and row['text']=='' and r._predictor.calls==0


def test_matrix_continues_after_changed_values_and_keeps_baseline(tmp_path,monkeypatch):
    import importlib.util,sys
    path=Path(__file__).resolve().parents[1]/'tools/experiment_matrix.py'
    spec=importlib.util.spec_from_file_location('matrix_v30',path);tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
    paths=[]
    for i in range(6):
        file=tmp_path/f'input{i}';file.write_bytes(b'test');paths.append(str(file))
    Path(paths[4]).with_suffix('.card.json').write_text('{}')
    calls=[]
    def run(command,**kwargs):
        name=command[command.index('--experiment')+1];out=Path(command[command.index('--output')+1]);out.mkdir()
        changed=name=='device';calls.append(name)
        row={'case':'gplx-01','phase':'warm','wallMs':10000,'readingCompleted':True,
             'baselineOcrDifferences':{'fullName':{'baseline':'A','current':'B'}} if changed else {},'stageTimings':[]}
        (out/'summary.json').write_text(json.dumps({'summary':{'gplx':{'warmMedianMs':10000}},'cases':[row]}))
        return SimpleNamespace(returncode=1 if changed else 0)
    monkeypatch.setattr(tool.subprocess,'run',run)
    argv=['check','--output',str(tmp_path/'matrix'),'--experiments','device','fixed','--repeats','2']
    for key,value in zip(('gplx-front','gplx-back','cccd-front','cccd-back','corner-weights','vietocr-python'),paths):argv+=['--'+key,value]
    monkeypatch.setattr(sys,'argv',argv)
    assert tool.main()==1 and calls==['v29','device','fixed']
    result=json.loads((tmp_path/'matrix/matrix.json').read_text())
    assert result['automaticWinnerSelected'] is False and result['experiments'][1]['status']=='NEEDS_INSPECTION'


def test_rescue_keeps_earlier_ambiguous_dates(monkeypatch,profile):
    from test_v25_staged import staged,line
    from test_v17_contract import raw
    import document_ai.reader.staged as module
    reader=staged(monkeypatch);reader.qr_enabled=False;reader.field_profile=profile;reader.recognition_scope='fields'
    monkeypatch.setenv('DOCUMENT_AI_FALLBACK_POLICY','profile')
    clean=[line('GIAY PHEP LAI XE',5),field_line('790000000001',40),field_line('NGUYỄN AN',85),field_line('23/12/2004'),field_line('A1',180)]
    def orientation(image,*a,**kw):
        is_front=image.getpixel((0,0))==(255,255,255)
        return {'status':'RESOLVED','rotationApplied':0,'image':image.resize((400,250)),
                'lines':clean if is_front else [line('CLASSIFICATION OF MOTOR VEHICLES')],'attempts':[],'reason':None}
    monkeypatch.setattr(module,'orient',orientation)
    def read(image,lines,**options):
        front=any(l['text']=='GIAY PHEP LAI XE' for l in lines)
        reader.ocr.last_scope={'status':'APPLIED' if front else 'FULL','orientationGeometry':{'status':'REUSED' if front else 'NOT_REUSED'}}
        return clean+[field_line('24/12/2004',145)] if front and not options.get('force_full_detection') else lines
    reader.ocr.read_oriented=read
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert result['ocrData']['dateOfBirth']['status']=='AMBIGUOUS'
    assert result['ocrData']['dateOfBirth']['candidate'] is None
    assert {c['value'] for c in result['ocrData']['dateOfBirth']['candidates']}=={'2004-12-23','2004-12-24'}


def test_v30_runner_scores_ocr_against_manual_labels_not_final_data(tmp_path,monkeypatch):
    import importlib.util,sys
    import document_ai.reader.factory as factory
    spec=importlib.util.spec_from_file_location('runner_v30',Path(__file__).resolve().parents[1]/'tools/test_v30.py')
    tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
    paths=[]
    for i in range(4):
        p=tmp_path/f'{i}.jpg';Image.new('RGB',(40,30),(i*40,0,0)).save(p);paths.append(str(p))
    weights=tmp_path/'m.pt';weights.write_bytes(b'm');weights.with_suffix('.card.json').write_text('{}')
    truth=tmp_path/'truth.json';truth.write_text(json.dumps({'VN_DRIVING_LICENSE':{'fullName':'MANUAL'},'VN_CITIZEN_ID':{'fullName':None}}))
    class Reader:
        qr_enabled=True;ocr=SimpleNamespace(close=lambda:None)
        def read(self,front,back,kind):
            if kind=='AUTO' or front==Path(paths[2]).read_bytes() and kind=='VN_DRIVING_LICENSE':return {'status':'RETAKE_REQUIRED'}
            keys=['documentNumber','fullName','dateOfBirth']+(['sex'] if kind=='VN_CITIZEN_ID' else ['licenseClass'])
            return {'status':'READ_SUCCESS','documentType':kind,'data':{'fullName':'MANUAL'},
                'executionReport':{'earlyExit':False},'checks':[{'side':s,'step':'OCR','status':'PASS','metrics':{'lineCount':1}} for s in ('FRONT','BACK')],
                'ocrData':{k:{'status':'EXTRACTED','candidate':{'value':'RAW_OCR'}} for k in keys}}
    monkeypatch.setattr(factory,'configured_reader',lambda:Reader())
    argv=['check','--output',str(tmp_path/'out'),'--experiment','device','--repeats','2','--truth',str(truth),'--corner-weights',str(weights),'--vietocr-python',sys.executable]
    for key,value in zip(('gplx-front','gplx-back','cccd-front','cccd-back'),paths):argv+=['--'+key,value]
    monkeypatch.setattr(sys,'argv',argv)
    assert tool.main()==1
    cases=json.loads((tmp_path/'out/summary.json').read_text())['cases']
    gplx=next(c for c in cases if c['case']=='gplx-01');cccd=next(c for c in cases if c['case']=='cccd-01')
    assert gplx['readingCompleted'] and gplx['truthPassed'] is False
    assert gplx['groundTruth'][0]['ocrValue']=='RAW_OCR' and cccd['truthPassed'] is None
