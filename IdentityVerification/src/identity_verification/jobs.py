"""One inference at a time; bounded, short-lived metadata/results, no image bytes."""
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from threading import Lock
from time import monotonic
from uuid import uuid4


class VerificationJobs:
    def __init__(self, inference_lock, ttl=600, limit=20):
        self.inference_lock = inference_lock
        self.ttl = ttl
        self.limit = limit
        self.lock = Lock()
        self.items = {}
        self.executor = ThreadPoolExecutor(max_workers=1)

    def _purge(self):
        now = monotonic()
        for key in list(self.items):
            item = self.items[key]
            if item.get('_finished') is not None and now - item['_finished'] > self.ttl:
                del self.items[key]
        finished = sorted((v['_finished'], k) for k, v in self.items.items() if '_finished' in v)
        while len(self.items) >= self.limit and finished:
            del self.items[finished.pop(0)[1]]

    def submit(self, work, cleanup):
        if not self.inference_lock.acquire(blocking=False):
            raise RuntimeError('BUSY')
        key = uuid4().hex
        try:
            with self.lock:
                self._purge()
                self.items[key] = dict(jobId=key, state='QUEUED', result=None, error=None,
                    events=[], progress=dict(schemaVersion='ai-progress/1', stage='QUEUED', percentage=None),
                    _started=monotonic())
            def run():
                def progress(event):
                    with self.lock:
                        item = self.items[key]
                        item['progress'] = dict(schemaVersion='ai-progress/1', percentage=None, **event)
                        previous = item['events'][-1] if item['events'] else None
                        if previous is None or previous['stage'] != event['stage']:
                            item['events'] = (item['events'] + [dict(stage=event['stage'], side='PAIR', elapsedMs=round((monotonic()-item['_started'])*1000))])[-80:]
                try:
                    with self.lock:
                        self.items[key]['state'] = 'RUNNING'
                    result = work(progress)
                    with self.lock:
                        self.items[key].update(state='COMPLETED', result=result)
                except Exception as error:
                    with self.lock:
                        self.items[key].update(state='FAILED', error={'type':type(error).__name__, 'status':422 if isinstance(error, ValueError) else 500})
                finally:
                    try:
                        cleanup()
                    finally:
                        with self.lock:
                            self.items[key]['_finished'] = monotonic()
                        self.inference_lock.release()
            self.executor.submit(run)
        except Exception:
            with self.lock:
                self.items.pop(key, None)
            self.inference_lock.release()
            raise
        return key

    def get(self, key):
        with self.lock:
            self._purge()
            item = self.items.get(key)
            if item is None:
                return None
            result = deepcopy({k:v for k,v in item.items() if not k.startswith('_')})
            result['elapsedMs'] = round((item.get('_finished', monotonic()) - item['_started']) * 1000)
            return result

    def close(self):
        self.executor.shutdown(wait=True)
