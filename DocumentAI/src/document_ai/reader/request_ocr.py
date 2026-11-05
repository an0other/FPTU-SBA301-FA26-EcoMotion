"""Request-local result reuse and retry budgets. No cross-request identity cache."""
import copy
import hashlib
import os


class RetryBudgetExceeded(RuntimeError):pass


class RequestOcr:
    def __init__(self,engine,max_retries=6):
        self.allow_optional_retry=os.getenv('DOCUMENT_AI_ADDRESS_RETRY','0')=='1'
        self.engine=engine;self.max_retries=max_retries;self.retry_calls=0;self.hits=0;self.cache={}
        self.implementation=getattr(engine,'implementation','configured-ocr')
        self.recognizer=getattr(engine,'recognizer',None)
        begin=getattr(engine,'begin_request',None)
        if begin:begin()
    def _key(self,image,kind,options=''):
        return kind+str(image.size)+options+hashlib.sha256(image.convert('RGB').tobytes()).hexdigest()
    def _run(self,key,action,retry=False):
        if key in self.cache:self.hits+=1;return copy.deepcopy(self.cache[key])
        if retry:
            if self.retry_calls>=self.max_retries:raise RetryBudgetExceeded('Request OCR retry budget exhausted')
            self.retry_calls+=1
        result=action()
        if len(self.cache)<32:self.cache[key]=copy.deepcopy(result)
        return result
    def read_oriented(self,image,lines=None,**options):
        method=getattr(self.engine,'read_oriented',None)
        if method:
            def read():
                rows=method(image,lines,**options)
                return {'lines':rows,'scope':copy.deepcopy(getattr(self.engine,'last_scope',{}))}
            result=self._run(self._key(image,'body',repr(options)),read)
            self.last_scope=result['scope']
            return result['lines']
        return self._run(self._key(image,'body'),lambda:self.engine.extract(image))
    def extract(self,image):
        return self._run(self._key(image,'retry'),lambda:self.engine.extract(image),retry=True)
    def recognize_line_crops(self,images):
        key='lines'+''.join(self._key(im,'crop') for im in images)
        return self._run(key,lambda:self.engine.recognize_line_crops(images),retry=True)
    def stats(self):
        return {'singleCropRecoveryRemaining':getattr(self.recognizer,'recovery_remaining',None),'retryCalls':self.retry_calls,'retryLimit':self.max_retries,'cacheHits':self.hits,
                'addressRetryEnabled':self.allow_optional_retry,'cacheScope':'THIS_REQUEST_ONLY','reusedObservationsAreIndependent':False}
