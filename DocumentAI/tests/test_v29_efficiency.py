"""Geometry/recognition reuse safety tests. Fake engines, not latency claims."""
import copy
from types import SimpleNamespace
import numpy as np
import pytest
from PIL import Image
from document_ai.adapters.ocr import crop_line
from document_ai.reader.fast_orientation import orient,preview,scale_lines
from document_ai.reader.vietocr_pipeline import DetectorOnlyVietOcrPipeline


class Detector:
    max_side=1280
    def __init__(self):self.calls=0
    def detect(self,image):
        self.calls+=1
        return [{'polygon':[[20,y],[280,y],[280,y+30],[20,y+30]],
                 'text':None,'confidence':None,'detectionConfidence':.95} for y in (40,100,160)]


class Recognizer:
    model='vgg_transformer'
    def __init__(self):self.crops=[]
    def recognize_many(self,images):
        self.crops.extend(im.copy() for im in images)
        return [{'text':'GIAY PHEP LAI XE','confidence':.99} for im in images]


def engine():
    d=Detector();r=Recognizer();e=DetectorOnlyVietOcrPipeline(object(),r,d)
    e.reuse_orientation_geometry=True;e.orientation_probe_limits=(2,4,6,12,None)
    e.begin_request();return e,d,r


def test_probe_and_body_use_identical_source_crops_and_one_detection():
    e,d,r=engine()
    pixels=np.random.default_rng(1).integers(0,256,(1440,2400,3),dtype=np.uint8)
    source=Image.fromarray(pixels);small=preview(source,1280)
    regions=d.detect(small);d.calls=0
    e.bind_orientation_source(small,source);e.probe_orientation(small,2)
    expected=crop_line(source,scale_lines(regions,small.size,source.size)[0]['polygon'])
    assert r.crops[0].tobytes()==expected.tobytes()
    meta=e.transfer_orientation_geometry(small,source)
    assert meta['status']=='REUSED'
    rows=e.read_oriented(source)
    assert len(rows)==3 and d.calls==1 and len(r.crops)==3
    assert e.last_timing['recognition']['reusedRegions']==2
    assert rows[0]['primaryText'] is None and meta['independentObservation'] is False


@pytest.mark.parametrize('angle',[0,90,180,270])
def test_orientation_transfers_only_verified_winning_rotation(angle):
    e,d,r=engine()
    original=Image.fromarray(np.random.default_rng(4).integers(0,256,(800,1600,3),dtype=np.uint8))
    incoming=original.rotate(angle,expand=True)
    result=orient(incoming,e,SimpleNamespace(predict=lambda im:{'angle':(-angle)%360,'confidence':.99}),1280,card_rectified=True)
    assert result['status']=='RESOLVED' and result['orientationGeometry']['status']=='REUSED'
    assert result['image'].tobytes()==original.tobytes()
    assert result['attempts'][0]['engineTimings']['probeLimit']==2
    e.read_oriented(result['image']);assert d.calls==1 and len(r.crops)==3


def test_lower_resolution_geometry_is_not_promoted_as_full_detection():
    e,d,r=engine();source=Image.new('RGB',(2400,1440));small=preview(source,960)
    e.bind_orientation_source(small,source);e.probe_orientation(small,2)
    meta=e.transfer_orientation_geometry(small,source)
    assert meta['status']=='NOT_REUSED' and meta['reason']=='DETECTION_RESOLUTION_MISMATCH'
    e.read_oriented(source);assert d.calls==2


def test_no_reuse_between_requests_even_for_same_card_bytes():
    e,d,r=engine();source=Image.new('RGB',(2400,1440));small=preview(source,1280)
    e.bind_orientation_source(small,source);e.probe_orientation(small,2)
    e.transfer_orientation_geometry(small,source);e.read_oriented(source)
    e.end_request();assert not e._orientation_sources and not e._transferred_geometry
    e.begin_request();e.read_oriented(source)
    assert d.calls==2 and len(r.crops)==6


def test_changed_pixels_cannot_hit_transferred_geometry_or_text_cache():
    e,d,r=engine();source=Image.new('RGB',(2400,1440));small=preview(source,1280)
    e.bind_orientation_source(small,source);e.probe_orientation(small,2);e.transfer_orientation_geometry(small,source)
    changed=source.copy();changed.putpixel((0,0),(255,0,0))
    e.read_oriented(changed);assert d.calls==2 and len(r.crops)==5


def test_forced_detection_bypasses_transferred_geometry():
    e,d,r=engine();source=Image.new('RGB',(2400,1440));small=preview(source,1280)
    e.bind_orientation_source(small,source);e.probe_orientation(small,2);e.transfer_orientation_geometry(small,source)
    e.read_oriented(source,force_full_detection=True)
    assert d.calls==2 and not e.last_timing['reusedDetection']
    assert e.last_scope['orientationGeometry']['status']=='NOT_REUSED'


def test_missing_core_ocr_rescued_without_consulting_qr(monkeypatch):
    from test_v25_staged import staged,line
    from test_v17_contract import raw
    reader=staged(monkeypatch);reader.qr_enabled=False
    old=reader.ocr.read_oriented;calls=[]
    def read(image,lines=None,**options):
        calls.append(options)
        result=old(image,lines,**options)
        if image.getpixel((0,0))==(255,255,255) and not options.get('force_full_detection'):
            reader.ocr.last_scope={'status':'FULL','orientationGeometry':{'status':'REUSED'}}
            return [line('GIAY PHEP LAI XE')]
        return result
    reader.ocr.read_oriented=read
    result=reader.read(raw('white'),raw('gray'),'VN_DRIVING_LICENSE')
    assert result['ocrData']['documentNumber']['candidate']
    checks=[c for c in result['checks'] if c['step']=='GEOMETRY_REUSE_FALLBACK']
    assert len(checks)==1 and checks[0]['metrics']['qrUsed'] is False
    assert sum(bool(c.get('force_full_detection')) for c in calls)==1
    assert result['recognitionScopeReport'][0]['firstPassLines']


def test_fast_qr_still_checks_both_sources_and_preserves_conflict():
    import qrcode
    from document_ai.adapters.qr import scan_qr
    a=qrcode.make('FIRST').convert('RGB');b=qrcode.make('OTHER').convert('RGB')
    result=scan_qr(a,{'stop_after_source_success':True},alternate_image=b)
    assert result.status=='INCONCLUSIVE' and result.payload['values']==['FIRST','OTHER']
    assert result.metrics['search_complete'] is False
    assert all(a['engine']=='zxingcpp' for a in result.metrics['attempts'])


def test_qr_fast_mode_keeps_opencv_fallback_when_zxing_finds_nothing(monkeypatch):
    import zxingcpp
    import qrcode
    from document_ai.adapters.qr import scan_qr
    monkeypatch.setattr(zxingcpp,'read_barcodes',lambda *a,**kw:[])
    result=scan_qr(qrcode.make('FALLBACK').convert('RGB'),{'stop_after_source_success':True})
    assert result.payload['values']==['FALLBACK']
    assert [r['engine'] for r in result.metrics['attempts']]==['zxingcpp','opencv']


def test_comparison_flags_data_changes_even_when_speed_improves():
    import importlib.util
    from pathlib import Path
    path=Path(__file__).resolve().parents[1]/'tools/compare_reader_runs.py'
    spec=importlib.util.spec_from_file_location('compare_reader_runs',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
    b={'summary':{'gplx':{'warmMedianMs':10000,'completed':2}}}
    e={'summary':{'gplx':{'warmMedianMs':8000,'completed':2}},'cases':[{'case':'gplx-01','phase':'warm','readingCompleted':True,'baselineOcrDifferences':{'fullName':{'baseline':'AN','current':'AM'}}}]}
    result=m.compare(b,e)
    assert result['timings'][0]['reductionPercent']==20 and result['ocrValueChanges']


def test_factory_profiles_preserve_cpu_default_and_baseline_route(monkeypatch):
    from document_ai.reader.factory import configured_reader
    import os
    for key in list(os.environ):
        if key.startswith('DOCUMENT_AI_'):monkeypatch.delenv(key)
    monkeypatch.setenv('DOCUMENT_AI_RECOGNIZER_DEVICE','cpu')
    monkeypatch.setenv('DOCUMENT_AI_READER_OPTIMIZATION','efficient')
    monkeypatch.setenv('DOCUMENT_AI_PIPELINE_MODE','staged')
    monkeypatch.delenv('DOCUMENT_AI_PREVIEW_MAX_SIDE',raising=False)
    monkeypatch.setenv('DOCUMENT_AI_DETECTION_MAX_SIDE','1280')
    reader=configured_reader()
    try:
        assert reader.preview_side==1280 and reader.ocr.reuse_orientation_geometry
        assert reader.ocr.orientation_probe_limits==(2,4,6,12,None)
        assert reader.ocr.recognizer.device=='cpu'
    finally:reader.ocr.close()
    monkeypatch.setenv('DOCUMENT_AI_READER_OPTIMIZATION','baseline')
    reader=configured_reader()
    try:
        assert reader.preview_side==960 and not reader.ocr.reuse_orientation_geometry
        assert reader.ocr.orientation_probe_limits==(6,12,None)
    finally:reader.ocr.close()


def test_validation_tool_compares_raw_ocr_and_keeps_completion_separate(tmp_path,monkeypatch):
    import importlib.util,sys
    from pathlib import Path
    import document_ai.reader.factory as factory
    path=Path(__file__).resolve().parents[1]/'tools/test_v30.py'
    spec=importlib.util.spec_from_file_location('v29_check',path);tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
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
                'ocrData':{k:{'status':'EXTRACTED','candidate':{'value':'NEW' if change[0] and k=='fullName' else 'same'}} for k in keys}}
    change=[False];monkeypatch.setattr(factory,'configured_reader',lambda:Reader())
    common=['check','--gplx-front',files[0],'--gplx-back',files[1],'--cccd-front',files[2],'--cccd-back',files[3],
        '--corner-weights',str(weights),'--vietocr-python',sys.executable,'--repeats','2','--target-ms','100000']
    monkeypatch.setattr(sys,'argv',common+['--output',str(tmp_path/'base'),'--optimization-profile','baseline'])
    assert tool.main()==0
    change[0]=True
    monkeypatch.setattr(sys,'argv',common+['--output',str(tmp_path/'new'),'--reference-summary',str(tmp_path/'base/summary.json')])
    assert tool.main()==1
    import json
    rows=json.loads((tmp_path/'new/summary.json').read_text())['cases']
    warm=[r for r in rows if r['phase']=='warm']
    assert all(r['readingCompleted'] and r['baselineValuesEquivalent'] is False for r in warm)
    assert all('fullName' in r['baselineOcrDifferences'] for r in warm)
