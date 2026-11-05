"""Authenticated FastAPI reader with one model instance and bounded inference."""
import hmac
import inspect
import os
import threading
import uuid
from pathlib import Path

from fastapi import Depends, FastAPI, UploadFile, File, Form, HTTPException, Request
from fastapi.responses import JSONResponse
from fastapi.security import APIKeyHeader

from .service import Reader
from .data import TYPES
from .models import ReaderResponse


def create_app(reader=None,warmup_files=None,api_key=None,environment=None,storage_root=None):
    from contextlib import asynccontextmanager
    from .factory import configured_reader
    from .pipeline_audit import PipelineAudit

    service_key=api_key if api_key is not None else os.getenv('DOCUMENT_AI_API_KEY','')
    if len(service_key)<32 or not service_key.isascii():
        raise ValueError('DOCUMENT_AI_API_KEY must contain at least 32 characters')
    run_environment=environment or os.getenv('DOCUMENT_AI_ENVIRONMENT','production')
    audit=PipelineAudit(run_environment,storage_root)
    max_upload_bytes=int(os.getenv('DOCUMENT_AI_MAX_UPLOAD_BYTES',str(8*1024*1024)))
    if not 1<=max_upload_bytes<=32*1024*1024:
        raise ValueError('DOCUMENT_AI_MAX_UPLOAD_BYTES must be between 1 and 33554432')

    service=reader or configured_reader()
    state={'state':'NOT_RUN','modelInferenceChecked':False}
    lock=threading.Lock()
    from .jobs import JobStore,ReaderBusy

    def start_job(job_id,front,back):
        return audit.begin(front,back,source='job:'+job_id)

    def record_job_result(job_id,result,front,back,trace_id):
        audit.record_result(result,front,back,source='job:'+job_id,trace_id=trace_id)

    def record_job_failure(job_id,error_type,front,back,trace_id):
        audit.record_failure(trace_id or job_id,error_type,front,back,source='job:'+job_id)

    jobs=JobStore(service,lock,on_complete=record_job_result,on_failure=record_job_failure,
                  on_start=start_job,on_progress=audit.record_progress,evidence_dir=audit.evidence_dir)
    if warmup_files is None:
        front=os.getenv('DOCUMENT_AI_WARMUP_FRONT');back=os.getenv('DOCUMENT_AI_WARMUP_BACK')
        if bool(front)!=bool(back):raise ValueError('Warm-up requires both front and back')
        warmup_files=(front,back) if front else None
    @asynccontextmanager
    async def lifespan(app):
        try:
            if warmup_files:
                import time
                from pathlib import Path
                start=time.perf_counter()
                def read(path):
                    with Path(path).open('rb') as stream:return stream.read(8388609)
                result=service.read(read(warmup_files[0]),read(warmup_files[1]),
                                    os.getenv('DOCUMENT_AI_WARMUP_DOCUMENT_TYPE','VN_DRIVING_LICENSE'))
                ocr_sides={c['side'] for c in result['checks'] if c['step']=='OCR' and c['status']=='PASS' and c.get('metrics',{}).get('lineCount',0)>0}
                if result['status'] in {'PROCESSING_ERROR','RETAKE_REQUIRED','UNSUPPORTED_DOCUMENT'} or ocr_sides!={'FRONT','BACK'}:
                    raise RuntimeError('Warm-up could not exercise OCR on both sides; inspect images/model logs')
                state.update(state='COMPLETED',modelInferenceChecked=True,
                             elapsedMs=round((time.perf_counter()-start)*1000,2))
            yield
        finally:
            jobs.close()
            close=getattr(service.ocr,'close',None)
            if close:close()
    app=FastAPI(title='DocumentAI local GPLX/CCCD reader',version='0.32.0',lifespan=lifespan)
    service_key_header=APIKeyHeader(name='X-API-Key',auto_error=False)

    @app.middleware('http')
    async def require_service_key(request:Request,call_next):
        if request.url.path.startswith('/v1/documents/'):
            provided=request.headers.get('X-API-Key','')
            if not provided:
                return JSONResponse(status_code=401,content={'detail':'Missing X-API-Key header'})
            if not hmac.compare_digest(provided.encode("utf-8"),service_key.encode("ascii")):
                return JSONResponse(status_code=401,content={'detail':'Invalid X-API-Key'})
        return await call_next(request)

    def read_upload(upload):
        content=upload.file.read(max_upload_bytes+1)
        if len(content)>max_upload_bytes:
            raise HTTPException(413,'Uploaded image exceeds the configured size limit')
        return content

    @app.get('/v1/capabilities')
    def capabilities():
        analyzer=getattr(service,'image_analysis',None)
        return {'version':'0.32.0','documentTypes':list(TYPES),'templateCoverage':'SINGLE_PILOT_LAYOUT_PER_TYPE',
            'imageAnalysisMode':analyzer.mode if analyzer else 'off','imageAnalyzers':analyzer.capabilities() if analyzer else [],
            'authenticityVerified':False,'automaticApproval':False,'singleActiveInference':True,'jobsStorage':'RAM_ONLY'}
    @app.get('/health')
    def health():return {'status':'UP','modelInferenceChecked':state['modelInferenceChecked'],'warmup':state}
    @app.get('/ready')
    def ready():
        if not state['modelInferenceChecked'] or not getattr(getattr(service.ocr,'detector',None),'is_ready',True):raise HTTPException(503,'Reader models are not ready; warm-up may be incomplete or detector worker failed')
        return {'status':'READY','modelInferenceChecked':True,'warmupElapsedMs':state.get('elapsedMs')}
    @app.post('/v1/documents/read',response_model=ReaderResponse,
              dependencies=[Depends(service_key_header)])
    def read_document(front:UploadFile=File(...),back:UploadFile=File(...),document_type:str=Form('AUTO')):
        if document_type not in ('AUTO',)+TYPES:raise HTTPException(422,'Unsupported document_type')
        front_bytes=read_upload(front)
        back_bytes=read_upload(back)
        if not lock.acquire(blocking=False):raise HTTPException(429,'Reader busy; retry later')
        trace_id=None
        try:
            trace_id=audit.begin(front_bytes,back_bytes)
            kwargs={}
            parameters=inspect.signature(service.read).parameters
            if trace_id and 'evidence_dir' in parameters:
                kwargs['evidence_dir']=audit.evidence_dir(trace_id)
            if getattr(service,'supports_progress',False):
                kwargs['progress']=lambda event:audit.record_progress(trace_id,event)
            result=service.read(front_bytes,back_bytes,document_type,**kwargs)
            audit.record_result(result,front_bytes,back_bytes,trace_id=trace_id)
            return result
        except Exception as error:
            audit.record_failure(trace_id or uuid.uuid4().hex,type(error).__name__,front_bytes,back_bytes)
            raise
        finally:lock.release()
    @app.post('/v1/documents/jobs',status_code=202,
              dependencies=[Depends(service_key_header)])
    def create_job(front:UploadFile=File(...),back:UploadFile=File(...),document_type:str=Form('AUTO')):
        if document_type not in ('AUTO',)+TYPES:raise HTTPException(422,'Unsupported document_type')
        front_bytes=read_upload(front)
        back_bytes=read_upload(back)
        try:key=jobs.submit(front_bytes,back_bytes,document_type)
        except ReaderBusy as error:raise HTTPException(429,str(error))
        return {'jobId':key,'pollUrl':'/v1/documents/jobs/'+key}
    @app.get('/v1/documents/jobs/{job_id}',
             dependencies=[Depends(service_key_header)])
    def get_job(job_id:str):
        result=jobs.get(job_id)
        if result is None:raise HTTPException(404,'Job not found or expired')
        return result
    @app.get('/reader',include_in_schema=False)
    def reader_demo():
        from fastapi.responses import HTMLResponse
        return HTMLResponse(
            '<!doctype html><html lang="en"><meta charset="utf-8">'
            '<title>DocumentAI service</title><body>'
            '<h1>DocumentAI service-to-service API</h1>'
            '<p>Direct browser inference is disabled. Call the protected API '
            'from an authorized backend or use tools/test_api_v32.py.</p>'
            '</body></html>'
        )
    return app
