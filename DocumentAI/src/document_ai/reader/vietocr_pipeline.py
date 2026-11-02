"""Paddle line geometry + independently recognized VietOCR text, no QR input."""
import json
import os
import base64
import queue
import subprocess
import tempfile
import threading
import time
import copy
import hashlib
import numpy as np
from pathlib import Path

from document_ai.adapters.ocr import crop_line


class VietOcrProcess:
    """One persistent worker in its own Python environment per Reader instance."""
    def __init__(self, python, model='vgg_seq2seq', device='cpu', timeout=600, log=None,cpu_threads=4,batch_size=1,transport="png"):
        if model not in {'vgg_seq2seq','vgg_transformer'}:
            raise ValueError('Unsupported VietOCR model')
        if not 1<=int(cpu_threads)<=32:raise ValueError("cpu_threads must be 1..32")
        if not 1<=int(batch_size)<=32:raise ValueError("batch_size must be 1..32")
        if transport not in {"png","raw"}:raise ValueError("Invalid crop transport")
        self.batch_size=int(batch_size);self.transport=transport
        self.cpu_threads=int(cpu_threads)
        self.python=str(Path(python).resolve())
        self.model=model;self.device=device;self.timeout=timeout;self.runtime={}
        self.process=None;self.recovery_remaining=4;self.log_path=log;self._lock=threading.Lock()
        self._folder=tempfile.TemporaryDirectory(prefix='documentai-vietocr-')
        self._replies=queue.Queue();self._request_id=0

    def _start(self):
        worker=Path(__file__).with_name('vietocr_worker.py')
        log=Path(self.log_path) if self.log_path else Path(self._folder.name)/'worker.log'
        log.parent.mkdir(parents=True,exist_ok=True)
        self._log=log.open('w',encoding='utf-8')
        self.process=subprocess.Popen([self.python,'-u',str(worker),'--model',self.model,
            '--device',self.device,'--cpu-threads',str(self.cpu_threads),'--batch-size',str(self.batch_size)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,
            stderr=self._log,text=True,encoding='utf-8',bufsize=1,shell=False)
        def read_output():
            for line in self.process.stdout:self._replies.put(line)
            self._replies.put(None)
        self._thread=threading.Thread(target=read_output,daemon=True)
        self._thread.start()

    def recognize_many(self, images):
        if not images:return []
        with self._lock:
            started=time.perf_counter();cold_process=self.process is None
            if cold_process:self._start()
            launched=time.perf_counter()
            paths=[];payload=[];self._request_id+=1
            if self.transport=='raw' and sum(im.width*im.height*3 for im in images)>24*1024*1024:
                raise ValueError('Raw crop payload exceeds 24 MiB; use png transport')
            for i,image in enumerate(images):
                if self.transport=='raw':
                    im=image.convert('RGB')
                    payload.append({'width':im.width,'height':im.height,'rgb':base64.b64encode(im.tobytes()).decode('ascii')})
                else:
                    path=Path(self._folder.name)/f'{self._request_id}-{i}.png'
                    image.save(path);paths.append(path);payload.append(str(path))
            encoded=time.perf_counter()
            try:
                self.process.stdin.write(json.dumps({'id':self._request_id,'images':payload,'transport':self.transport,'recoveryLimit':self.recovery_remaining})+'\n')
                self.process.stdin.flush()
                try:line=self._replies.get(timeout=self.timeout)
                except queue.Empty:
                    self.process.kill()
                    raise TimeoutError('VietOCR worker timed out; inspect worker log')
                if line is None:raise RuntimeError('VietOCR worker exited; inspect worker log')
                reply=json.loads(line)
                if reply.get('id')!=self._request_id:raise RuntimeError('VietOCR worker protocol mismatch')
                if reply.get('status')!='OK':
                    raise RuntimeError('VietOCR worker failed: '+str(reply.get('errorType'))+'; inspect worker log')
                self.runtime=reply.get('runtime',{})
                self.recovery_remaining=max(0,self.recovery_remaining-int(self.runtime.get('singleRetryCount',0)))
                self.last_timing={'workerRequestId':self._request_id,'singleRetryCount':self.runtime.get('singleRetryCount',0),'failedCropIndexes':self.runtime.get('failedCropIndexes',[]),'coldProcess':cold_process,'launchMs':round((launched-started)*1000,2),
                    'encodeMs':round((encoded-launched)*1000,2),
                    'workerRoundTripMs':round((time.perf_counter()-encoded)*1000,2),
                    'workerInitializationMs':self.runtime.get('initializationMs'),
                    'workerInferenceMs':self.runtime.get('inferenceMs'),'cropCount':len(images),
                    'batchGroups':copy.deepcopy(self.runtime.get('batchGroups',[])),
                    'experimentSettings':self.runtime.get('experimentSettings',{})}
                rows=reply['results']
                if len(rows)!=len(images):raise RuntimeError('VietOCR result count mismatch')
                return rows
            finally:
                for path in paths:path.unlink(missing_ok=True)

    def close(self):
        if self.process is not None:
            if self.process.poll() is None:
                self.process.stdin.close()
                try:self.process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    self.process.kill();self.process.wait(timeout=5)
            self.process.stdout.close();self._log.close()
        self._folder.cleanup()


class VietOcrPipeline:
    """Paddle remains responsible for text regions/orientation; output text is VietOCR.
    Primary text is evidence only: never a fallback for failed VietOCR recognition.
    """
    def __init__(self, primary, recognizer):
        self.orientation_engine=primary
        self.primary=primary;self.recognizer=recognizer
        self.implementation='paddle-regions/vietocr-'+recognizer.model
        self.request_timings=[];self._observation_cache=None;self._crop_cache=None

    def begin_request(self):
        # No reuse across uploads, including benchmark repeats. One fixed engine per instance.
        self._observation_cache={};self._crop_cache={};self._region_cache={};self._transferred_geometry={};self._orientation_sources={};self.request_timings=[]
        begin=getattr(getattr(self,'detector',None),'begin_request',None)
        if begin:begin()
        if hasattr(self.recognizer,'recovery_remaining'):self.recognizer.recovery_remaining=4

    def end_request(self):
        self._observation_cache=None;self._crop_cache=None;self._region_cache={};self._transferred_geometry={};self._orientation_sources={}

    def recognize_detected(self, image, lines):
        started=time.perf_counter();pixels=np.asarray(image.convert('RGB'))
        cache=self._observation_cache
        source_key=hashlib.sha256(pixels.tobytes()).hexdigest() if cache is not None else None
        crops=[];missing=[];keys=[];crop_keys={};predictions=[None]*len(lines);pixel_hits=0
        pixel_cache=self._crop_cache if os.getenv('DOCUMENT_AI_PIXEL_REUSE','0')=='1' else None
        for index,line in enumerate(lines):
            key=(source_key,str(image.size),json.dumps(line['polygon'],separators=(',',':')))
            keys.append(key)
            if cache is not None and key in cache:predictions[index]=copy.deepcopy(cache[key])
            else:
                crop=crop_line(image,line['polygon'],source_pixels=pixels)
                crop_key=(crop.size,hashlib.sha256(crop.tobytes()).hexdigest()) if pixel_cache is not None else None
                if pixel_cache is not None and crop_key in pixel_cache:
                    predictions[index]=copy.deepcopy(pixel_cache[crop_key]);pixel_hits+=1
                else:
                    crops.append(crop);missing.append(index);crop_keys[index]=crop_key
        cropped=time.perf_counter()
        fresh=self.recognizer.recognize_many(crops) if crops else []
        if len(fresh)!=len(missing):raise RuntimeError('Recognizer count mismatch')
        for index,row in zip(missing,fresh):
            predictions[index]=row
            if cache is not None and len(cache)<256:cache[keys[index]]=copy.deepcopy(row)
            if pixel_cache is not None and len(pixel_cache)<256:pixel_cache[crop_keys[index]]=copy.deepcopy(row)
        self.last_recognition_timing={'cropAndLookupMs':round((cropped-started)*1000,2),
            'recognizerWallMs':round((time.perf_counter()-cropped)*1000,2),
            'failedRegions':sum(row.get('status')=='ERROR' for row in predictions),'requestedRegions':len(lines),'newRegions':len(missing),'reusedRegions':len(lines)-len(missing),'exactPixelReuseHits':pixel_hits,
            'transport':copy.deepcopy(getattr(self.recognizer,'last_timing',{})) if crops else {}}
        result=[]
        for original,prediction in zip(lines,predictions):
            result.append({**original,'text':prediction['text'],
                'confidence':prediction['confidence'],
                'recognition_status':'ERROR' if prediction.get('status')=='ERROR' else 'SINGLE_ENGINE_UNVALIDATED',
                'recognitionError':prediction.get('errorType') if prediction.get('status')=='ERROR' else None,
                'rawRecognitionResult':{k:prediction[k] for k in ('rawReturnedText','rawConfidence','singleRetryError','recoveredFrom') if k in prediction},
                'recognitionEngine':'vietocr-'+self.recognizer.model,
                'primaryText':original.get('text'),'primaryConfidence':original.get('confidence'),
                'recognitionMs':prediction.get('elapsedMs')})
        return result

    def recognize_line_crops(self,images):
        return [{**row,'recognitionEngine':'vietocr-'+self.recognizer.model}
                for row in self.recognizer.recognize_many(images)]

    def extract(self,image):
        return self.recognize_detected(image,self.primary.extract(image))

    def close(self):
        try:self.recognizer.close()
        finally:
            close=getattr(getattr(self,'detector',None),'close',None)
            if close:close()


class DetectorOnlyVietOcrPipeline(VietOcrPipeline):
    """Header OCR is orientation evidence only; full card/ROI text comes from VietOCR."""
    def __init__(self,primary,recognizer,detector):
        super().__init__(primary,recognizer)
        self.detector=detector
        self.implementation='paddle-detection-only/vietocr-'+recognizer.model
        self.reuse_orientation_geometry=False
        self.orientation_probe_limits=(6,12,None)
        self._transferred_geometry={};self._orientation_sources={}

    def detect_cached(self,image):
        key=(image.size,hashlib.sha256(image.convert('RGB').tobytes()).hexdigest()) if self._observation_cache is not None else None
        cached=key is not None and key in self._region_cache
        if cached:return copy.deepcopy(self._region_cache[key]),True
        regions=self.detector.detect(image)
        if key is not None and len(self._region_cache)<16:self._region_cache[key]=copy.deepcopy(regions)
        return regions,False

    def bind_orientation_source(self,preview_image,full_image):
        """Bind this exact orientation preview to its unresized source pixels."""
        if not self.reuse_orientation_geometry or self._observation_cache is None:return
        key=(preview_image.size,hashlib.sha256(preview_image.convert('RGB').tobytes()).hexdigest())
        # Only the current probe needs source pixels; do not retain eight large rotations.
        self._orientation_sources={key:full_image}

    def transfer_orientation_geometry(self,preview_image,full_image):
        """Transfer geometry only. Recognition always uses full source pixels.

        Only a verified orientation in this request may call this method. Keys bind
        both cached previews and targets to exact pixel content, not filenames.
        """
        from .fast_orientation import scale_lines
        if not self.reuse_orientation_geometry or self._observation_cache is None:
            return {'status':'DISABLED'}
        key=(preview_image.size,hashlib.sha256(preview_image.convert('RGB').tobytes()).hexdigest())
        regions=self._region_cache.get(key)
        if not regions:return {'status':'NOT_REUSED','reason':'NO_CACHED_GEOMETRY'}
        # Reuse at the body detector's resolution, never a lower resolution shortcut.
        expected=full_image.copy();cap=getattr(self.detector,'max_side',1280)
        expected.thumbnail((cap,cap))
        if preview_image.size!=expected.size:
            return {'status':'NOT_REUSED','reason':'DETECTION_RESOLUTION_MISMATCH'}
        mapped=scale_lines(regions,preview_image.size,full_image.size)
        for row in mapped:
            p=np.asarray(row.get('polygon'),dtype=float)
            if p.shape!=(4,2) or not np.isfinite(p).all():
                return {'status':'NOT_REUSED','reason':'INVALID_GEOMETRY'}
            if (p[:,0]<0).any() or (p[:,1]<0).any() or (p[:,0]>full_image.width).any() or (p[:,1]>full_image.height).any():
                return {'status':'NOT_REUSED','reason':'GEOMETRY_OUT_OF_BOUNDS'}
        target=(full_image.size,hashlib.sha256(full_image.convert('RGB').tobytes()).hexdigest())
        if len(self._region_cache)>=16:return {'status':'NOT_REUSED','reason':'REQUEST_CACHE_LIMIT'}
        self._region_cache[target]=copy.deepcopy(mapped)
        meta={'status':'REUSED','regionCount':len(mapped),'previewSize':list(preview_image.size),
              'sourceSize':list(full_image.size),'recognitionSource':'FULL_RESOLUTION_PIXELS',
              'scope':'THIS_REQUEST_ONLY','independentObservation':False}
        self._transferred_geometry[target]=meta
        return copy.deepcopy(meta)

    def probe_orientation(self,image,limit=6):
        # Detect all geometry, recognize short representative lines first. No QR/type label input.
        started=time.perf_counter();regions,cached=self.detect_cached(image)
        detected=time.perf_counter()
        def order(region):
            p=np.asarray(region['polygon'],dtype=float)
            w=max(np.linalg.norm(p[1]-p[0]),np.linalg.norm(p[2]-p[3]))
            h=max(np.linalg.norm(p[3]-p[0]),np.linalg.norm(p[2]-p[1]))
            ratio=w/max(h,1)
            return (not 1.3<=ratio<=24,float(p[:,1].mean()),float(p[:,0].mean()))
        ordered=sorted(regions,key=order)
        selected=ordered if limit is None else ordered[:limit]
        source_key=(image.size,hashlib.sha256(image.convert('RGB').tobytes()).hexdigest())
        source=self._orientation_sources.get(source_key)
        if source is not None:
            from .fast_orientation import scale_lines
            # Both the probe and final OCR crop the exact original pixels with
            # the same mapped polygons. Reuse is not independent corroboration.
            rows=self.recognize_detected(source,scale_lines(selected,image.size,source.size))
            rows=scale_lines(rows,source.size,image.size)
        else:rows=self.recognize_detected(image,selected)
        self.last_timing={'purpose':'ORIENTATION_PROBE','recognitionSource':'FULL_RESOLUTION_PIXELS' if source is not None else 'PREVIEW_PIXELS','detectionMs':round((detected-started)*1000,2),
            'reusedDetection':cached,'detectedCount':len(regions),'selectedCount':len(selected),
            'probeLimit':limit,'recognition':self.last_recognition_timing,
            'totalMs':round((time.perf_counter()-started)*1000,2)}
        self.request_timings.append(copy.deepcopy(self.last_timing))
        return rows

    def read_oriented(self,image,orientation_lines=None,side=None,expected_type=None,field_profile=None,force_full_detection=False):
        started=time.perf_counter()
        key=(image.size,hashlib.sha256(image.convert('RGB').tobytes()).hexdigest())
        if force_full_detection:
            self._region_cache.pop(key,None);self._transferred_geometry.pop(key,None)
        transfer=copy.deepcopy(self._transferred_geometry.get(key,{}))
        regions,cached=self.detect_cached(image)
        detected=time.perf_counter()
        self.last_scope={'status':'FULL','detectedCount':len(regions),'recognizedCount':len(regions),'skippedRegions':[]}
        if side is not None:
            from .field_scope import select_field_regions
            regions,self.last_scope=select_field_regions(regions,image,field_profile,side,expected_type,orientation_lines or [])
        direct_report={}
        if os.getenv('DOCUMENT_AI_FIELD_READ_MODE','detected')=='direct' and side=='FRONT' and expected_type=='VN_DRIVING_LICENSE' and field_profile and self.last_scope.get('status')=='APPLIED':
            from .direct_fields import profile_regions
            regions,direct_report=profile_regions(image,regions,field_profile)
        rows=self.recognize_detected(image,regions)
        if direct_report:self.last_scope['directFields']=direct_report
        self.last_scope['orientationGeometry']=transfer or {'status':'NOT_REUSED'}
        self.last_timing={'detectionMs':round((detected-started)*1000,2),'reusedDetection':cached,'orientationGeometry':transfer,
            'detector':{} if cached else copy.deepcopy(getattr(self.detector,'last_timing',{})),
            'recognition':self.last_recognition_timing,'totalMs':round((time.perf_counter()-started)*1000,2)}
        self.request_timings.append(copy.deepcopy(self.last_timing))
        return rows

    def extract(self,image):return self.read_oriented(image)
