"""Regression checks with fake inference, not model accuracy/latency claims."""
import copy,json
from types import SimpleNamespace
import pytest
from PIL import Image
from document_ai.reader.field_regions import parse_field_lines,observe_profile,region_extract,field_labels
from document_ai.reader.targeted_rescue import rescue_missing
from document_ai.reader.experiments import environment


@pytest.fixture(autouse=True)
def isolated_experiment(monkeypatch):
    # CLI tests for old tools run main() in-process and can leave environment settings.
    for key,value in environment('fixed').items():monkeypatch.setenv(key,value)


def line(text,y=10,x=10,width=100):
    return dict(text=text,confidence=.97,polygon=[[x,y],[x+width,y],[x+width,y+12],[x,y+12]],recognitionEngine='test')


@pytest.fixture
def profile(tmp_path):
    p=tmp_path/'p.json';p.write_text(json.dumps({'id':'pilot','documentType':'VN_DRIVING_LICENSE','contextMarginFraction':0,
        'fields':{'documentNumber':[.5,.1,.95,.3],'fullName':[.5,.3,.95,.45],'dateOfBirth':[.5,.45,.95,.63],'licenseClass':[.5,.65,.95,.90]}}))
    return p


def input_rows():
    return [line('790000000001',40,210),line('NGUYỄN AN',85,210),line('02/01/2000',130,210)]


def test_gplx_270_expires_k_is_not_second_license_class(monkeypatch):
    monkeypatch.setenv('DOCUMENT_AI_FIELD_LABEL_POLICY','owned')
    rows=[line('Hạng/Class: A1'),line('Có giá trị đến/Expires: K',28)]
    value,used=parse_field_lines('licenseClass',rows)
    assert value=='A1' and [r['text'] for r in used]==['Hạng/Class: A1']
    monkeypatch.setenv('DOCUMENT_AI_FIELD_LABEL_POLICY','legacy')
    assert parse_field_lines('licenseClass',rows)[0] is None


def test_genuine_class_conflict_is_not_voted_away(profile):
    rows=[line('Hạng/Class: A1',180,210),line('Hạng/Class: A2',200,210)]
    no_ocr=SimpleNamespace(extract=lambda im:pytest.fail('No voting'))
    result=region_extract(rows,'VN_DRIVING_LICENSE',Image.new('RGB',(400,250)),profile,no_ocr,retry_policy='selective',field_keys={'licenseClass'})
    assert result['licenseClass']['status']=='AMBIGUOUS'
    assert {c['value'] for c in result['licenseClass']['candidates']}=={'A1','A2'}


@pytest.mark.parametrize('key,text',[('licenseClass','Expires: K'),('documentNumber','Date of birth: 790000000001'),('fullName','Address: NGUYỄN AN')])
def test_foreign_label_cannot_own_another_value(key,text):
    assert parse_field_lines(key,[line(text)])[0] is None


def test_label_boundaries():
    assert 'licenseClass' not in field_labels('tháng 06 năm 2023')
    assert 'licenseClass' not in field_labels('CLASSIFICATION OF MOTOR VEHICLES')


def test_targeted_rescue_only_reads_missing_roi(profile):
    image=Image.new('RGB',(400,250));rows=input_rows();original=copy.deepcopy(rows);calls=[]
    parsed=observe_profile(rows,'VN_DRIVING_LICENSE',image,profile)
    def extract(im):calls.append(im.size);return [line('Hạng/Class: A1')]
    new,fields,report=rescue_missing(image,rows,parsed,profile,SimpleNamespace(extract=extract))
    assert calls==[(180,63)] and rows==original
    assert fields['licenseClass']['candidate']['value']=='A1'
    assert new[-1]['polygon'][0]==[210,172] and report['qrUsed'] is False


def test_targeted_does_not_read_away_ambiguity(profile):
    image=Image.new('RGB',(400,250));rows=input_rows()+[line('A1',180,210),line('A2',200,210)]
    parsed=observe_profile(rows,'VN_DRIVING_LICENSE',image,profile)
    _,out,r=rescue_missing(image,rows,parsed,profile,SimpleNamespace(extract=lambda im:pytest.fail('No vote')))
    assert r['reason']=='VALID_AMBIGUITY_RETAINED' and out['licenseClass']['status']=='AMBIGUOUS'


def test_many_missing_fields_fall_back_to_full(profile):
    image=Image.new('RGB',(400,250));parsed=observe_profile([],'VN_DRIVING_LICENSE',image,profile)
    _,_,r=rescue_missing(image,[],parsed,profile,SimpleNamespace(extract=lambda im:pytest.fail('No ROI storm')))
    assert r['reason']=='TOO_MANY_MISSING_FIELDS'


def test_failed_roi_cannot_fill_from_another_source(profile):
    image=Image.new('RGB',(400,250));rows=input_rows();parsed=observe_profile(rows,'VN_DRIVING_LICENSE',image,profile)
    _,out,r=rescue_missing(image,rows,parsed,profile,SimpleNamespace(extract=lambda im:[]))
    assert out['licenseClass']['candidate'] is None and r['remainingFields']==['licenseClass']


def test_global_width_scheduler_avoids_split_batch_and_preserves_order(monkeypatch):
    from document_ai.adapters.ocr import VietOcrRecognizer
    from document_ai.reader.experimental_predictor import resized_width
    class Predictor:
        config={'dataset':{'image_height':32,'image_min_width':10,'image_max_width':512}}
        def __init__(self):self.forward_widths=[]
        def predict_batch(self,images,return_prob=True):
            self.forward_widths.extend(sorted(set(resized_width(im.width,im.height,self.config['dataset']) for im in images)))
            return [str(im.getpixel((0,0))[0]) for im in images],[.95]*len(images)
    images=[Image.new('RGB',(w,32),(i,0,0)) for i,w in enumerate([80,80,80,100,100,100])]
    e=VietOcrRecognizer();e._ensure_predictor=lambda:None;e._predictor=Predictor()
    monkeypatch.setenv('DOCUMENT_AI_BATCH_SCHEDULER','legacy');old=e.recognize_batch(images,4)
    assert len(e._predictor.forward_widths)==3
    e._predictor=Predictor();monkeypatch.setenv('DOCUMENT_AI_BATCH_SCHEDULER','global');new=e.recognize_batch(images,4)
    assert len(e._predictor.forward_widths)==2
    assert [r['text'] for r in old]==[r['text'] for r in new]==list(map(str,range(6)))
    assert all(g['scheduler']=='global' for g in e.last_batch_groups)


def test_invalid_scheduler_is_not_ignored(monkeypatch):
    from document_ai.adapters.ocr import VietOcrRecognizer
    e=VietOcrRecognizer();e._ensure_predictor=lambda:None;e._predictor=SimpleNamespace(predict_batch=lambda *a,**k:None)
    monkeypatch.setenv('DOCUMENT_AI_BATCH_SCHEDULER','bad')
    with pytest.raises(ValueError):e.recognize_batch([Image.new('RGB',(20,10))])


def test_pixel_reuse_requires_exact_crop_and_request(monkeypatch):
    from test_v29_efficiency import engine
    e,_,r=engine();monkeypatch.setenv('DOCUMENT_AI_PIXEL_REUSE','1')
    image=Image.new('RGB',(400,250),'white');a={'polygon':[[20,40],[280,40],[280,70],[20,70]]}
    e.recognize_detected(image,[a]);count=len(r.crops)
    b={'polygon':[[20.000000001,40],[280,40],[280,70],[20,70]]}
    e.recognize_detected(image.copy(),[b]);assert len(r.crops)==count
    assert e.last_recognition_timing['exactPixelReuseHits']==1
    changed=image.copy();changed.putpixel((40,50),(0,0,0));e.recognize_detected(changed,[b]);assert len(r.crops)==count+1
    e.end_request();assert e._crop_cache is None
    e.begin_request();e.recognize_detected(image,[a]);assert len(r.crops)==count+2


def test_orientation_title_can_verify_alternate_angle(monkeypatch):
    from document_ai.reader.fast_orientation import orient
    monkeypatch.setenv('DOCUMENT_AI_ORIENTATION_EXIT','text_verified');calls=[]
    def extract(image):
        calls.append(1);return [line('unrelated' if len(calls)==1 else 'GIAY PHEP LAI XE',width=250)]
    result=orient(Image.new('RGB',(800,450)),SimpleNamespace(extract=extract),SimpleNamespace(predict=lambda im:{'angle':0,'confidence':.99}),card_rectified=True)
    assert len(calls)==2 and result['rotationApplied']==180 and result['status']=='RESOLVED'
    assert result['earlyAcceptanceBasis']=='PRINTED_TITLE_AND_HORIZONTAL_TEXT_ON_RECTIFIED_CARD'


def test_orientation_never_accepts_classifier_only(monkeypatch):
    from document_ai.reader.fast_orientation import orient
    monkeypatch.setenv('DOCUMENT_AI_ORIENTATION_EXIT','text_verified')
    result=orient(Image.new('RGB',(800,450)),SimpleNamespace(extract=lambda im:[line('unrelated')]),SimpleNamespace(predict=lambda im:{'angle':0,'confidence':1.}),card_rectified=True)
    assert result['status']=='INCONCLUSIVE' and result['earlyAcceptanceBasis'] is None


def test_core_scope_only_supported_gplx_front(profile,monkeypatch):
    from document_ai.reader.field_scope import select_field_regions
    image=Image.new('RGB',(400,250));outside=line('optional address',145,30,width=80);name=line('holder',85,210)
    monkeypatch.setenv('DOCUMENT_AI_FIELD_SCOPE','core')
    rows,scope=select_field_regions([outside,name],image,profile,'FRONT','VN_DRIVING_LICENSE',[line('GIAY PHEP LAI XE')])
    assert rows==[name] and scope['fieldScope']=='core'
    rows,scope=select_field_regions([outside,name],image,profile,'FRONT','VN_CITIZEN_ID',[line('CAN CUOC CONG DAN')])
    assert rows==[outside,name] and scope['status']=='FULL'


def test_optional_fields_are_not_retried_in_core_scope(profile):
    no_ocr=SimpleNamespace(extract=lambda im:pytest.fail('No optional read'))
    out=region_extract(input_rows()+[line('A1',180,210)],'VN_DRIVING_LICENSE',Image.new('RGB',(400,250)),profile,no_ocr,retry_policy='always',field_keys={'documentNumber','dateOfBirth','licenseClass'})
    assert set(out)=={'documentNumber','dateOfBirth','licenseClass'}


def test_presets_isolate_padding_and_fp16():
    assert environment('v29')['DOCUMENT_AI_FIELD_LABEL_POLICY']=='legacy'
    assert environment('fixed')['DOCUMENT_AI_FIELD_LABEL_POLICY']=='owned'
    assert environment('targeted')['DOCUMENT_AI_RESCUE_POLICY']=='targeted'
    assert environment('exact')['DOCUMENT_AI_DECODE_BACKEND']=='legacy'
    assert environment('combined')['DOCUMENT_AI_PRECISION']=='fp32'
    assert environment('combined')['DOCUMENT_AI_PREPROCESSOR']=='numpy'
    assert environment('combined')['DOCUMENT_AI_FIELD_SCOPE']=='full'
    assert environment('fp16')['DOCUMENT_AI_PRECISION']=='fp16'


def test_staged_targeted_rescue_avoids_unnecessary_whole_card_reread(profile,monkeypatch):
    from test_v25_staged import staged
    from test_v17_contract import raw
    import document_ai.reader.staged as module
    reader=staged(monkeypatch);reader.qr_enabled=False;reader.field_profile=profile;reader.recognition_scope='fields'
    monkeypatch.setenv('DOCUMENT_AI_RESCUE_POLICY','targeted')
    monkeypatch.setenv('DOCUMENT_AI_FALLBACK_POLICY','profile')
    front=[line('GIAY PHEP LAI XE',2)]+input_rows()
    def orient(image,*args,**kwargs):
        is_front=image.getpixel((0,0))==(255,255,255)
        return dict(status='RESOLVED',rotationApplied=0,image=image.resize((400,250)),lines=front if is_front else [line('CLASSIFICATION OF MOTOR VEHICLES')],attempts=[],reason=None)
    monkeypatch.setattr(module,'orient',orient);calls=[]
    def read(image,lines,**options):
        calls.append(options)
        reader.ocr.last_scope={'status':'APPLIED','orientationGeometry':{'status':'REUSED'}}
        return lines
    reader.ocr.read_oriented=read;reader.ocr.extract=lambda im:[line('Hạng/Class: A1')]
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert len(calls)==2 and not any(c.get('force_full_detection') for c in calls)
    assert result['ocrData']['licenseClass']['candidate']['value']=='A1'
    assert result['executionReport']['optimizationCounters']['targetedFieldAttempts']==1
    assert result['executionReport']['optimizationCounters']['fullGeometryRescues']==0


def test_targeted_failure_still_runs_full_rescue(profile,monkeypatch):
    from test_v25_staged import staged
    from test_v17_contract import raw
    import document_ai.reader.staged as module
    reader=staged(monkeypatch);reader.qr_enabled=False;reader.field_profile=profile;reader.recognition_scope='fields'
    monkeypatch.setenv('DOCUMENT_AI_RESCUE_POLICY','targeted')
    monkeypatch.setenv('DOCUMENT_AI_FALLBACK_POLICY','profile')
    front=[line('GIAY PHEP LAI XE',2)]+input_rows()
    def orient(image,*args,**kwargs):
        is_front=image.getpixel((0,0))==(255,255,255)
        return dict(status='RESOLVED',rotationApplied=0,image=image.resize((400,250)),lines=front if is_front else [line('CLASSIFICATION OF MOTOR VEHICLES')],attempts=[],reason=None)
    monkeypatch.setattr(module,'orient',orient);calls=[]
    def read(image,lines,**options):
        calls.append(options);reader.ocr.last_scope={'status':'APPLIED','orientationGeometry':{'status':'REUSED'}}
        return list(lines)+([line('Hạng/Class: A1',180,210)] if options.get('force_full_detection') else [])
    reader.ocr.read_oriented=read;reader.ocr.extract=lambda im:[]
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert sum(bool(c.get('force_full_detection')) for c in calls)==1
    assert result['ocrData']['licenseClass']['candidate']['value']=='A1'


def test_matrix_records_all_profiles_and_optional_reverse_confirmation(tmp_path,monkeypatch):
    import importlib.util,sys
    from pathlib import Path
    path=Path(__file__).resolve().parents[1]/'tools/experiment_matrix_v31.py'
    spec=importlib.util.spec_from_file_location('matrix_v31',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    f=tmp_path/'input';f.write_bytes(b'x');f.with_suffix('.card.json').write_text('{}')
    argv=['matrix','--output',str(tmp_path/'out'),'--include-all','--confirm-order','--device','cpu']
    for key in ('gplx-front','gplx-back','cccd-front','cccd-back','corner-weights','vietocr-python'):argv+=['--'+key,str(f)]
    monkeypatch.setattr(sys,'argv',argv)
    def fake_run(cmd,**kwargs):
        out=Path(cmd[cmd.index('--output')+1]);out.mkdir()
        report={'summary':{},'cases':[]}
        (out/'summary.json').write_text(json.dumps(report));return SimpleNamespace(returncode=0)
    monkeypatch.setattr(m.subprocess,'run',fake_run)
    assert m.main()==0
    output=json.loads((tmp_path/'out/matrix.json').read_text())
    assert output['automaticWinnerSelected'] is False
    rows=output['experiments'];assert [r['name'] for r in rows[-3:]]==['combined','fixed','v29']
    assert next(r for r in rows if r['name']=='fp16')['status']=='SKIPPED'
    assert rows[-1]['round']=='confirmation'


@pytest.mark.parametrize('name',['NGUYỄN THỊ HẰNG','TRẦN VĂN HÀNG','ĐẶNG THỊ TRANG'])
def test_holder_name_is_not_misclassified_as_a_label(name):
    from document_ai.reader.data import normalize_field
    assert field_labels(name)==set()
    assert normalize_field('fullName',name)==name
    assert parse_field_lines('fullName',[line(name)])[0]==name


@pytest.mark.parametrize('key,text,value',[
    ('fullName','Họ tên/Full name: NGUYỄN AN','NGUYỄN AN'),
    ('dateOfBirth','Ngày sinh/Date of birth: 02/01/2000','2000-01-02'),
    ('nationality','Quốc tịch/Nationality: VIỆT NAM','Việt Nam')])
def test_inline_label_value_is_not_discarded(key,text,value):
    assert parse_field_lines(key,[line(text)])[0]==value
