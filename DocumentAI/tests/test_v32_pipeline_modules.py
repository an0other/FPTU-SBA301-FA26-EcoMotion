"""Module boundaries and decisions, not accuracy claims for image forensics."""
import hashlib,io,json
from types import SimpleNamespace
from PIL import Image
import pytest
from document_ai.reader.image_analysis import AnalysisInput,AnalysisResult,ImageAnalysisRunner
from document_ai.reader.pipeline_report import enrich


def source(fmt='JPEG'):
    return AnalysisInput(Image.new('RGB',(160,100),'white'),'FRONT','test-sha',fmt)


class Checker:
    id='test_checker';version='test/v1';capabilities=('test_signal',)
    def analyze(self,source):return AnalysisResult('PASS',metrics={'observed':True})


def test_disabled_analyzers_never_execute():
    class MustNotRun(Checker):
        def analyze(self,source):pytest.fail('disabled')
    rows=ImageAnalysisRunner('off',[MustNotRun()]).run(source())
    assert rows[0]['status']=='SKIPPED' and rows[0]['decisionUsed'] is False


def test_diagnostics_only_report_metrics_without_pass_or_fake():
    rows=ImageAnalysisRunner('diagnostic').run(source())
    assert [r['status'] for r in rows]==['INCONCLUSIVE','NOT_IMPLEMENTED','NOT_IMPLEMENTED']
    assert all(not r['decisionUsed'] for r in rows)
    assert 'ela_mean_abs_difference' in rows[0]['metrics']
    assert rows[0]['inputSource']=='EXIF_ORIENTED_ORIGINAL_BEFORE_RECTIFICATION'
    assert rows[0]['metrics']['thresholdsValidated'] is False
    assert rows[0]['scope']=='IMAGE_SIGNAL_ONLY_NOT_DOCUMENT_AUTHENTICITY'


def test_png_is_not_treated_as_previous_jpeg_compression():
    row=ImageAnalysisRunner('diagnostic').run(source('PNG'))[0]
    assert row['metrics']['elaApplicability']=='NOT_APPLICABLE_NON_JPEG'
    assert 'ela_mean_abs_difference' not in row['metrics']
    assert 'fft_high_frequency_energy_ratio' in row['metrics']


def test_mutating_analyzer_does_not_change_reader_source():
    class Mutator(Checker):
        def analyze(self,s):s.image.putpixel((0,0),(0,0,0));return AnalysisResult('INCONCLUSIVE')
    s=source();ImageAnalysisRunner('diagnostic',[Mutator()]).run(s)
    assert s.image.getpixel((0,0))==(255,255,255)


@pytest.mark.parametrize('result',[AnalysisResult('APPROVED'),AnalysisResult('PASS',metrics={'x':float('nan')}),AnalysisResult('PASS',reasons='bad')])
def test_invalid_plugin_results_become_error(result):
    checker=Checker();checker.analyze=lambda s:result
    row=ImageAnalysisRunner('diagnostic',[checker]).run(source())[0]
    assert row['status']=='ERROR' and row['reasons']==['IMAGE_ANALYZER_EXECUTION_ERROR']


def test_plugin_exception_isolated_and_other_plugins_continue():
    class Broken(Checker):
        id='broken'
        def analyze(self,s):raise RuntimeError('model missing')
    rows=ImageAnalysisRunner('diagnostic',[Broken(),Checker()]).run(source())
    assert [r['status'] for r in rows]==['ERROR','PASS']


def test_unsafe_evidence_names_are_rejected(tmp_path):
    checker=Checker();checker.analyze=lambda s:AnalysisResult('INCONCLUSIVE',evidence={'../../escape.png':b'x'})
    rows=ImageAnalysisRunner('diagnostic',[checker]).run(source(),tmp_path)
    assert rows[0]['status']=='ERROR' and not (tmp_path/'escape.png').exists()


def test_optional_evidence_saved_in_analyzer_folder(tmp_path):
    rows=ImageAnalysisRunner('diagnostic').run(source(),tmp_path)
    assert (tmp_path/'front/image-analysis/ela_fft_diagnostics/ela.png').is_file()
    assert len(rows[0]['evidence'])==1


@pytest.mark.parametrize('ids',[['same','same'],['../unsafe']])
def test_registry_rejects_invalid_or_duplicate_ids(ids):
    checkers=[]
    for key in ids:c=Checker();c.id=key;checkers.append(c)
    with pytest.raises(ValueError):ImageAnalysisRunner('diagnostic',checkers)


def bare_result(kind='VN_CITIZEN_ID'):
    keys=['documentNumber','fullName','dateOfBirth','sex' if kind=='VN_CITIZEN_ID' else 'licenseClass']
    return dict(status='READ_SUCCESS',documentType=kind,data={},ocrData={k:{'candidate':{'value':'test'}} for k in keys},
        fields={},checks=[],warnings=[],assessment={})


@pytest.mark.parametrize('signal',['PASS','FAIL','INCONCLUSIVE','ERROR','NOT_IMPLEMENTED'])
def test_image_result_does_not_turn_into_approval_or_fraud(signal):
    result=enrich(bare_result(),[{'status':signal}],'diagnostic',[])
    assert result['status']=='READ_SUCCESS'
    assert result['pipelineReport']['workflowStatus']=='AWAITING_ADMIN_REVIEW'
    assert result['assessment']['approval']['automaticApproval'] is False
    assert result['assessment']['approval']['documentVerified'] is False
    assert result['imageAnalysis']['authenticityVerified'] is False
    assert result['assessment']['documentAuthenticity']['status']=='NOT_ESTABLISHED'
    assert result['assessment']['documentAuthenticity']['verified'] is False


def test_image_analyzer_registry_uses_metadata_not_concrete_classes():
    class DeclaredAnalyzer(Checker):
        implementation='MODEL_ADAPTER'
        scope='CAPTURE_SIGNAL_ONLY'
    capability=ImageAnalysisRunner('diagnostic',[DeclaredAnalyzer()]).capabilities()[0]
    assert capability['implementation']=='MODEL_ADAPTER'
    assert capability['scope']=='CAPTURE_SIGNAL_ONLY'
    assert capability['authenticityVerified'] is False


def test_authenticity_summary_does_not_turn_clean_signals_into_authenticity():
    from document_ai.reader.document_authenticity import build_document_authenticity
    rows=[
        {'id':'digital_tampering','status':'PASS'},
        {'id':'screen_print_recapture','status':'PASS'},
    ]
    summary=build_document_authenticity(rows)
    assert summary['dimensions']['digitalIntegrity']['status']=='NO_ANOMALY_DETECTED'
    assert summary['dimensions']['captureIntegrity']['status']=='NO_ANOMALY_DETECTED'
    assert summary['dimensions']['issuerAndProvenance']['status']=='NOT_IMPLEMENTED'
    assert summary['dimensions']['holderBinding']['status']=='NOT_VERIFIED'
    assert summary['status']=='NOT_ESTABLISHED' and summary['verified'] is False


def test_authenticity_signal_failure_requests_review_not_fake_verdict():
    from document_ai.reader.document_authenticity import build_document_authenticity
    summary=build_document_authenticity([
        {'id':'digital_tampering','status':'FAIL'},
    ])
    assert summary['dimensions']['digitalIntegrity']['status']=='FLAGGED_FOR_REVIEW'
    assert summary['dimensions']['captureIntegrity']['status']=='NOT_IMPLEMENTED'
    assert summary['status']=='NOT_ESTABLISHED' and summary['verified'] is False


def test_cccd_has_reading_expiry_and_approval_assessment():
    r=bare_result();r['data']['expiryDate']='2000-01-01'
    out=enrich(r,[],'off',[])
    assert out['assessment']['readingAssessment']['status']=='CORE_FIELDS_READ'
    assert out['assessment']['expiryCheck']['status']=='EXPIRED_DATE'
    assert out['assessment']['expiryCheck']['legalValidity']=='NOT_VERIFIED'
    assert out['assessment']['documentAuthenticity']['dimensions']['printedExpiryDate']['status']=='EXPIRED_DATE'
    assert out['assessment']['documentAuthenticity']['dimensions']['currentValidity']['status']=='NOT_IMPLEMENTED'


def test_pipeline_quality_failure_prevents_image_analysis_and_ocr(monkeypatch):
    from test_v25_staged import staged
    from test_v17_contract import raw
    import document_ai.reader.staged as module
    from document_ai.core import StepOutput,Status
    reader=staged(monkeypatch)
    reader.image_analysis=SimpleNamespace(mode='diagnostic',capabilities=lambda:[],run=lambda *a:pytest.fail('Quality failed'))
    monkeypatch.setattr(module,'quality',lambda *a:StepOutput(Status.FAIL,['BLUR']))
    result=reader.read(raw('white'),raw('gray'))
    assert result['status']=='RETAKE_REQUIRED' and reader.ocr.calls==0
    assert result['pipelineReport']['workflowStatus']=='NEEDS_RETAKE'
    assert result['imageAnalysis']['coverage']=='NOT_RUN'


def test_pair_conflict_prevents_image_analysis(monkeypatch):
    from test_v25_staged import staged,line
    from test_v17_contract import raw
    import document_ai.reader.staged as module
    reader=staged(monkeypatch);reader.qr_enabled=False;original=module.orient
    def orient(image,*a,**k):
        r=original(image,*a,**k)
        if image.getpixel((0,0))==(255,255,255):r['lines']=[line('CAN CUOC CONG DAN')]
        return r
    monkeypatch.setattr(module,'orient',orient)
    reader.image_analysis=SimpleNamespace(mode='diagnostic',capabilities=lambda:[],run=lambda *a:pytest.fail('Mixed pair'))
    result=reader.read(raw('white'),raw('gray'))
    assert result['pipelineReport']['workflowStatus']=='NEEDS_RETAKE' and reader.ocr.calls==0


def test_hook_uses_original_before_detailed_ocr_and_preserves_reading(monkeypatch):
    from test_v25_staged import staged
    from test_v17_contract import raw
    reader=staged(monkeypatch);reader.qr_enabled=False;seen=[];old=reader.ocr.read_oriented
    class Capture(Checker):
        def analyze(self,s):seen.append(('image',s.side,s.sha256,s.source_format));return AnalysisResult('INCONCLUSIVE')
    reader.image_analysis=ImageAnalysisRunner('diagnostic',[Capture()])
    def read(*a,**k):seen.append(('ocr',));return old(*a,**k)
    reader.ocr.read_oriented=read;front,back=raw('white'),raw('gray');events=[]
    result=reader.read(front,back,'VN_DRIVING_LICENSE',progress=events.append)
    assert [r[0] for r in seen]==['image','image','ocr','ocr']
    assert seen[0][2]==hashlib.sha256(front).hexdigest() and seen[0][3]=='PNG'
    assert result['status']=='READ_SUCCESS'
    stages=[e['stage'] for e in events]
    assert stages.index('ANALYZING_IMAGE_SIGNALS')<stages.index('READING_FIELDS')
    assert result['pipelineReport']['samePhysicalCardVerified'] is False


def test_api_serialization_keeps_new_reports_for_read_and_jobs(monkeypatch):
    import time
    from fastapi.testclient import TestClient
    from test_v25_staged import staged
    from test_v17_contract import raw
    from document_ai.reader.api import create_app
    reader=staged(monkeypatch);reader.image_analysis=ImageAnalysisRunner('diagnostic')
    with TestClient(create_app(reader=reader,api_key='test-api-key-for-document-ai-tests-0001',
                               environment='production'),
                    headers={'X-API-Key':'test-api-key-for-document-ai-tests-0001'}) as client:
        cap=client.get('/v1/capabilities').json();assert cap['version']=='0.32.0'
        assert cap['automaticApproval'] is False and cap['imageAnalysisMode']=='diagnostic'
        files={'front':('front.jpg',raw('white'),'image/jpeg'),'back':('back.jpg',raw('gray'),'image/jpeg')}
        result=client.post('/v1/documents/read',files=files,data={'document_type':'VN_DRIVING_LICENSE'})
        assert result.status_code==200 and result.json()['imageAnalysis']['status']=='INCONCLUSIVE'
        job=client.post('/v1/documents/jobs',files=files,data={'document_type':'VN_DRIVING_LICENSE'}).json()
        for _ in range(100):
            state=client.get(job['pollUrl']).json()
            if state['state'] in {'COMPLETED','FAILED'}:break
            time.sleep(.01)
        assert state['state']=='COMPLETED' and state['result']['pipelineReport']['automaticApproval'] is False


def test_http_test_tool_records_complete_read_jobs_and_negatives(tmp_path,monkeypatch):
    import importlib.util,sys
    from pathlib import Path
    path=Path(__file__).resolve().parents[1]/'tools/test_api_v32.py'
    spec=importlib.util.spec_from_file_location('api_test_v32',path);tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
    monkeypatch.setenv('DOCUMENT_AI_API_KEY','test-api-key-for-document-ai-tests-0001')
    argv=['test','--output',str(tmp_path/'out')]
    values={}
    for key in ('gplx-front','gplx-back','cccd-front','cccd-back'):
        f=tmp_path/key;f.write_bytes(key.encode());values[key]=key.encode();argv+=['--'+key,str(f)]
    jobs={}
    def call(base,path,method='GET',payload=None,**kwargs):
        if path=='/ready':return 200,{'status':'READY'},1
        if path=='/v1/capabilities':return 200,{'version':'0.32.0'},1
        if method=='GET':return 200,{'state':'COMPLETED','result':jobs[path]},1
        negative=not payload['front'] or payload['front']==payload['back'] or payload['front']==values['cccd-front'] and payload['back']==values['gplx-back']
        result={'status':'RETAKE_REQUIRED' if negative else 'READ_SUCCESS','pipelineReport':{'automaticApproval':False},'ocrData':{}}
        if path.endswith('/jobs'):
            poll='/v1/documents/jobs/'+str(len(jobs));jobs[poll]=result;return 202,{'jobId':str(len(jobs)),'pollUrl':poll},1
        return 200,result,1
    monkeypatch.setattr(tool,'call',call);monkeypatch.setattr(sys,'argv',argv)
    assert tool.main()==0
    report=json.loads((tmp_path/'out/api-test.json').read_text())
    assert len(report['cases'])==7 and all(c['passed'] for c in report['cases'])
