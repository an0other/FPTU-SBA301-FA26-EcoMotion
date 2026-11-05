import threading
from fastapi.testclient import TestClient
from document_ai.reader.api import create_app
from test_v17_contract import fake_reader,raw


def test_jobs_show_actual_events_and_share_inference_lock(monkeypatch):
    reader=fake_reader(monkeypatch);original=reader.read
    entered=threading.Event();release=threading.Event()
    reader.supports_progress=True
    def read(*args,progress=None,**kwargs):
        if progress:
            progress({'stage':'VALIDATING_FILES','side':'PAIR','elapsedMs':0})
            entered.set()
            if not release.wait(3):raise RuntimeError('Test synchronization timeout')
            progress({'stage':'READING_FIELDS','side':'PAIR','elapsedMs':5})
        return original(*args,**kwargs)
    reader.read=read
    files={'front':('f.png',raw('white')),'back':('b.png',raw('gray'))}
    with TestClient(create_app(reader,api_key='test-api-key-for-document-ai-tests-0001'),
                    headers={'X-API-Key':'test-api-key-for-document-ai-tests-0001'}) as client:
        submitted=client.post('/v1/documents/jobs',files=files,data={'document_type':'VN_DRIVING_LICENSE'})
        assert submitted.status_code==202
        assert entered.wait(2)
        path=submitted.json()['pollUrl'];pending=client.get(path).json()
        assert pending['state']=='RUNNING' and pending['events'][0]['stage']=='VALIDATING_FILES'
        assert client.post('/v1/documents/read',files=files).status_code==429
        assert client.post('/v1/documents/jobs',files=files).status_code==429
        release.set()
        for _ in range(100):
            result=client.get(path).json()
            if result['state']=='COMPLETED':break
            threading.Event().wait(.01)
        assert result['state']=='COMPLETED'
        assert result['result']['status']=='READ_SUCCESS'
        assert result['events'][-1]['stage']=='READING_FIELDS'
        assert client.get('/reader').status_code==200
        assert client.get('/v1/documents/jobs/not-a-real-job').status_code==404


def test_failed_job_releases_lock_and_returns_no_traceback(monkeypatch):
    reader=fake_reader(monkeypatch)
    reader.read=lambda *args,**kwargs:(_ for _ in ()).throw(RuntimeError('private input not to expose'))
    files={'front':('f.png',raw('white')),'back':('b.png',raw('gray'))}
    with TestClient(create_app(reader,api_key='test-api-key-for-document-ai-tests-0001'),
                    headers={'X-API-Key':'test-api-key-for-document-ai-tests-0001'}) as client:
        path=client.post('/v1/documents/jobs',files=files).json()['pollUrl']
        for _ in range(100):
            result=client.get(path).json()
            if result['state']=='FAILED':break
            threading.Event().wait(.01)
        assert result['state']=='FAILED' and result['error']=={'type':'RuntimeError'}
        assert client.post('/v1/documents/jobs',files=files).status_code==202


def test_job_ttl_and_memory_result_cap(monkeypatch):
    from document_ai.reader.jobs import JobStore
    import time
    store=JobStore(object(),threading.Lock(),ttl=10,max_results=2)
    now=time.monotonic()
    store.items={'old':{'state':'COMPLETED','_finished':now-11,'_started':now-20},
                 'new':{'state':'COMPLETED','_finished':now,'_started':now,'result':{}}}
    assert store.get('old') is None
    assert store.get('new') is not None
    store.close()
