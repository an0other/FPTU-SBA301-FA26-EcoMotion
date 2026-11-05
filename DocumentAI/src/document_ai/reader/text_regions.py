"""Standalone Paddle text detection. No text or recognition confidence invented."""
import json
import math
import time
import numpy as np


class PaddleTextRegions:
    def __init__(self,device='cpu',model_name='PP-OCRv6_tiny_det',model=None):
        self.device=device;self.model_name=model_name;self._model=model

    def ensure_model(self):
        if self._model is None:
            from paddleocr import TextDetection
            self._model=TextDetection(model_name=self.model_name,device=self.device,
                                      enable_mkldnn=False,enable_hpi=False)
    def detect(self,image,progress=None):
        started=time.perf_counter();cold=self._model is None
        self.ensure_model()
        initialized=time.perf_counter()
        if progress:progress('PREPARING_INPUT')
        pixels=np.asarray(image.convert('RGB'))[:,:,::-1].copy()
        prepared=time.perf_counter()
        if progress:progress('PREDICT_AND_NATIVE_POSTPROCESS')
        outputs=list(self._model.predict(input=pixels,batch_size=1))
        predicted=time.perf_counter()
        if progress:progress('PARSING_AND_VALIDATING_OUTPUT')
        if len(outputs)!=1:raise ValueError('Expected one text detection result')
        value=outputs[0].json
        if callable(value):value=value()
        if isinstance(value,str):value=json.loads(value)
        value=value.get('res',value)
        polygons=value.get('dt_polys',[]);scores=value.get('dt_scores',[])
        if len(polygons)!=len(scores):raise ValueError('Inconsistent detection output lengths')
        regions=[]
        for polygon,score in zip(polygons,scores):
            points=np.asarray(polygon,dtype=float);score=float(score)
            if points.shape!=(4,2) or not np.isfinite(points).all():raise ValueError('Invalid text quadrilateral')
            if not math.isfinite(score) or not 0<=score<=1:raise ValueError('Invalid detection score')
            if (points[:,0]<-1).any() or (points[:,0]>image.width+1).any() or (points[:,1]<-1).any() or (points[:,1]>image.height+1).any():
                raise ValueError('Text quadrilateral outside image')
            regions.append({'polygon':points.tolist(),'detectionConfidence':score,
                            'text':None,'confidence':None,'primaryRecognition':'NOT_RUN'})
        self.last_timing={'coldModel':cold,'initializationMs':round((initialized-started)*1000,2),
            'prepareInputMs':round((prepared-initialized)*1000,2),
            'predictAndNativePostprocessMs':round((predicted-prepared)*1000,2),
            'parseAndValidationMs':round((time.perf_counter()-predicted)*1000,2),
            'inferenceAndParseMs':round((time.perf_counter()-initialized)*1000,2),
            'inputSize':list(image.size),'regionCount':len(regions)}
        return regions
