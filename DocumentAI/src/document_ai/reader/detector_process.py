"""Supervised resident detector; a timeout kills the native inference process."""
import base64,json,queue,subprocess,sys,tempfile,threading,time
from pathlib import Path


class DetectorFailure(RuntimeError):
    fatal_for_request=True
    def __init__(self,reason,metadata):
        super().__init__(reason);self.reason=reason;self.metadata=metadata


class DetectorTimeout(DetectorFailure):pass


class DetectorProcess:
    def __init__(self,python=None,device='cpu',model='PP-OCRv6_tiny_det',timeout=8.,startup_timeout=120.,log=None,worker_script=None):
        if not .05<=float(timeout)<=60 or not .05<=float(startup_timeout)<=300:raise ValueError('Invalid detector timeout')
        self.python=python or sys.executable;self.device=device;self.model=model
        self.timeout=float(timeout);self.startup_timeout=float(startup_timeout)
        self.worker_script=Path(worker_script) if worker_script else Path(__file__).with_name('detector_worker.py')
        self._temp=tempfile.TemporaryDirectory(prefix='documentai-detector-')
        self.log_path=Path(log) if log else Path(self._temp.name)/'detector.log'
        self.process=None;self._queue=None;self._thread=None;self._log=None;self._lock=threading.Lock()
        self._counter=0;self.last_timing={};self.last_failure=None;self._failed_request=False;self._closed=False

    def _receive(self,deadline,phase,key=None):
        remaining=deadline-time.perf_counter()
        try:
            if remaining<=0:raise queue.Empty()
            line=self._queue.get(timeout=remaining)
        except queue.Empty:
            meta={'phase':phase,'workerRequestId':key,'timeoutSeconds':self.startup_timeout if phase=='STARTUP' else self.timeout,
                  'lastWorkerStage':getattr(self,'_last_stage',None),'workerPid':self.process.pid if self.process else None}
            self._stop();self._failed_request=True;self.last_failure={'reason':'DETECTOR_TIMEOUT',**meta}
            raise DetectorTimeout('DETECTOR_TIMEOUT',meta)
        if line is None:
            self._fail('DETECTOR_WORKER_EXITED',phase,key)
        try:return json.loads(line)
        except (TypeError,ValueError):self._fail('DETECTOR_PROTOCOL_ERROR',phase,key)

    def _fail(self,reason,phase,key=None):
        meta={'phase':phase,'workerRequestId':key,'lastWorkerStage':getattr(self,'_last_stage',None)}
        self._stop();self._failed_request=True;self.last_failure={'reason':reason,**meta};raise DetectorFailure(reason,meta)

    def _start(self):
        self.log_path.parent.mkdir(parents=True,exist_ok=True);self._log=self.log_path.open('a',encoding='utf-8')
        proc=subprocess.Popen([str(self.python),'-u',str(self.worker_script),'--device',self.device,'--model',self.model],
            stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=self._log,text=True,encoding='utf-8',bufsize=1,shell=False)
        self.process=proc;q=queue.Queue();self._queue=q
        def pump():
            try:
                for line in proc.stdout:q.put(line)
            finally:q.put(None)
        self._thread=threading.Thread(target=pump,daemon=True);self._thread.start()
        reply=self._receive(time.perf_counter()+self.startup_timeout,'STARTUP')
        if reply.get('type')!='READY':self._fail('DETECTOR_STARTUP_ERROR','STARTUP')
        return reply.get('startupMs')

    def begin_request(self):
        self._failed_request=False;self.last_failure=None

    @property
    def is_ready(self):return self.process is not None and self.process.poll() is None and not self._failed_request

    def detect(self,image):
        with self._lock:
            if self._closed:raise RuntimeError('Detector closed')
            if self._failed_request:raise DetectorFailure(self.last_failure['reason'],self.last_failure)
            if min(image.size)<2 or max(image.size)>2560:raise ValueError('Detector input must be 2..2560 pixels per side')
            started=time.perf_counter();self._last_stage=None;self.last_failure=None
            cold=self.process is None;startup_ms=self._start() if cold else 0
            self._counter+=1;key=self._counter;encode_start=time.perf_counter()
            rgb=image.convert('RGB');payload={'id':key,'width':rgb.width,'height':rgb.height,'rgb':base64.b64encode(rgb.tobytes()).decode('ascii')}
            encode_ms=(time.perf_counter()-encode_start)*1000
            deadline=time.perf_counter()+self.timeout
            proc=self.process;wire=json.dumps(payload)+'\n';sent=queue.Queue()
            def write_input():
                try:proc.stdin.write(wire);proc.stdin.flush();sent.put(None)
                except (BrokenPipeError,OSError,ValueError) as error:sent.put(type(error).__name__)
            writer=threading.Thread(target=write_input,daemon=True);writer.start()
            try:write_error=sent.get(timeout=max(.001,deadline-time.perf_counter()))
            except queue.Empty:
                self._last_stage='WRITING_INPUT'
                self._receive(time.perf_counter()-1,'INFERENCE',key)
            if write_error:self._fail('DETECTOR_PIPE_CLOSED','INFERENCE',key)
            events=[]
            while True:
                reply=self._receive(deadline,'INFERENCE',key)
                if reply.get('id')!=key:self._fail('DETECTOR_PROTOCOL_ERROR','INFERENCE',key)
                if reply.get('type')=='STAGE':
                    self._last_stage=reply.get('stage');events.append(self._last_stage);continue
                if reply.get('type')!='RESULT':self._fail('DETECTOR_EXECUTION_ERROR','INFERENCE',key)
                rows=reply.get('regions')
                if not isinstance(rows,list):self._fail('DETECTOR_PROTOCOL_ERROR','INFERENCE',key)
                self.last_timing={**reply.get('timing',{}),'workerRequestId':key,'coldProcess':cold,
                    'workerStartupMs':startup_ms,'transportEncodeMs':round(encode_ms,2),
                    'parentWallMs':round((time.perf_counter()-started)*1000,2),'workerStages':events,
                    'inferenceTimeoutSeconds':self.timeout}
                return rows

    def _stop(self):
        proc=self.process;self.process=None
        if proc is not None:
            if proc.poll() is None:proc.kill()
            try:proc.wait(timeout=2)
            except subprocess.TimeoutExpired:pass
            for pipe in (proc.stdin,proc.stdout):
                if pipe:
                    try:pipe.close()
                    except OSError:pass
        if self._log:self._log.close();self._log=None

    def close(self):
        with self._lock:
            self._closed=True;self._stop();self._temp.cleanup()
