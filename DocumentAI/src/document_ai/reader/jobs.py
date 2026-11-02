"""Single active inference, bounded RAM-only results, actual stage events."""
import copy
import inspect
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from .models import ReaderResponse


class ReaderBusy(RuntimeError):pass


class JobStore:
    def __init__(self,reader,inference_lock,ttl=600,max_results=20,on_complete=None,on_failure=None,
                 on_start=None,on_progress=None,evidence_dir=None):
        self.reader=reader;self.inference_lock=inference_lock;self.ttl=ttl;self.max_results=max_results
        self.on_complete=on_complete;self.on_failure=on_failure
        self.on_start=on_start;self.on_progress=on_progress;self.evidence_dir=evidence_dir
        self.items={};self.lock=threading.Lock();self.executor=ThreadPoolExecutor(max_workers=1)
        self.closed=False
    def _purge(self):
        now=time.monotonic()
        expired=[k for k,v in self.items.items() if v['state'] in {'COMPLETED','FAILED'} and now-v['_finished']>self.ttl]
        for key in expired:del self.items[key]
        completed=sorted((v['_finished'],k) for k,v in self.items.items() if v['state'] in {'COMPLETED','FAILED'})
        while len(self.items)>=self.max_results and completed:
            _,key=completed.pop(0);del self.items[key]
    def submit(self,front,back,kind):
        if not self.inference_lock.acquire(blocking=False):raise ReaderBusy('Reader busy; retry later')
        key=uuid.uuid4().hex
        try:
            with self.lock:
                if self.closed:raise ReaderBusy('Reader stopping')
                self._purge()
                self.items[key]={'jobId':key,'state':'QUEUED','events':[], 'result':None,'error':None,'_started':time.monotonic()}
            def run():
                trace_id=None
                try:
                    with self.lock:self.items[key]['state']='RUNNING'
                    if self.on_start:trace_id=self.on_start(key,front,back)
                    def progress(event):
                        with self.lock:self.items[key]['events']=(self.items[key]['events']+[dict(event)])[-80:]
                        if self.on_progress:self.on_progress(trace_id,event)
                    kwargs={}
                    parameters=inspect.signature(self.reader.read).parameters
                    if trace_id and self.evidence_dir and 'evidence_dir' in parameters:
                        kwargs['evidence_dir']=self.evidence_dir(trace_id)
                    if getattr(self.reader,'supports_progress',False):
                        kwargs['progress']=progress
                        result=self.reader.read(front,back,kind,**kwargs)
                    else:
                        progress({'stage':'PROCESSING','side':'PAIR','elapsedMs':0})
                        result=self.reader.read(front,back,kind,**kwargs)
                    result=ReaderResponse.model_validate(result).model_dump()
                    if self.on_complete:self.on_complete(key,result,front,back,trace_id)
                    with self.lock:self.items[key].update(state='COMPLETED',result=result,_finished=time.monotonic())
                except Exception as error:
                    if self.on_failure:
                        try:self.on_failure(key,type(error).__name__,front,back,trace_id)
                        except Exception as audit_error:error=audit_error
                    with self.lock:self.items[key].update(state='FAILED',error={'type':type(error).__name__},_finished=time.monotonic())
                finally:self.inference_lock.release()
            self.executor.submit(run)
        except Exception:
            with self.lock:self.items.pop(key,None)
            self.inference_lock.release();raise
        return key
    def get(self,key):
        with self.lock:
            self._purge()
            job=self.items.get(key)
            if job is None:return None
            result=copy.deepcopy({k:v for k,v in job.items() if not k.startswith('_')})
            result['elapsedMs']=round((job.get('_finished',time.monotonic())-job['_started'])*1000,2)
            return result
    def close(self):
        with self.lock:self.closed=True
        self.executor.shutdown(wait=True)
