"""Explicit inference experiments, wrapping the already loaded VietOCR model.

Exact-width mode preserves upstream resizing. Bucket mode pads right edges;
this can change text/confidence and must be evaluated before adoption.
"""
import math
import time
from contextlib import nullcontext
import numpy as np
from PIL import Image
from .image_preprocessing import Normalizer


def resized_width(width,height,dataset):
    return min(dataset['image_max_width'],max(dataset['image_min_width'],math.ceil(int(dataset['image_height']*width/height)/10)*10))


class ExperimentalPredictor:
    def __init__(self,predictor,policy='exact',decoder='device',precision='fp32',preprocessor='numpy',bucket_width=64,max_batch=8):
        if policy not in {'exact','bucket'} or decoder not in {'legacy','device'} or precision not in {'fp32','fp16'}:raise ValueError('Invalid inference experiment')
        if not 1<=max_batch<=32 or not 8<=bucket_width<=256:raise ValueError('Invalid batching limits')
        if precision=='fp16' and not str(predictor.device).startswith('cuda'):raise ValueError('FP16 experiment requires explicit CUDA device')
        self.base=predictor;self.policy=policy;self.decoder=decoder;self.precision=precision
        self.normalizer=Normalizer(preprocessor);self.bucket_width=bucket_width;self.max_batch=max_batch;self.last_report={}
    def predict(self,img,return_prob=False):
        # Single-crop numeric recovery stays on the reference predictor.
        return self.base.predict(img,return_prob=return_prob)
    def predict_batch(self,images,return_prob=False):
        import torch
        d=self.base.config['dataset'];groups={};start=time.perf_counter()
        for index,image in enumerate(images):
            width=resized_width(image.width,image.height,d)
            bucket=width if self.policy=='exact' else min(d['image_max_width'],math.ceil(width/self.bucket_width)*self.bucket_width)
            groups.setdefault(bucket,[]).append((index,image,width))
        texts=[None]*len(images);scores=[None]*len(images);reports=[];truncated_images=[];preprocess_ms=0;inference_ms=0
        self.base.model.eval()
        for bucket,items in sorted(groups.items()):
            for offset in range(0,len(items),self.max_batch):
                chunk=items[offset:offset+self.max_batch];began=time.perf_counter()
                pixels=np.empty((len(chunk),d['image_height'],bucket,3),dtype=np.uint8)
                for j,(_,image,width) in enumerate(chunk):
                    rgb=np.asarray(image.convert('RGB').resize((width,d['image_height']),Image.Resampling.LANCZOS))
                    pixels[j,:,:width]=rgb
                    if width<bucket:pixels[j,:,width:]=rgb[:,-1:,:]
                host=self.normalizer.prepare(pixels)
                tensor=torch.from_numpy(host).to(self.base.device)
                preprocess_ms+=(time.perf_counter()-began)*1000
                amp=torch.autocast(device_type='cuda',dtype=torch.float16) if self.precision=='fp16' else nullcontext()
                began=time.perf_counter()
                with torch.inference_mode(),amp:
                    if self.decoder=='device':
                        from .experimental_decode import device_translate
                        ids,prob,decode_report=device_translate(tensor,self.base.model)
                    else:
                        from vietocr.tool.translate import translate
                        ids,prob=translate(tensor,self.base.model);decode_report={'decoder':'legacy'}
                for local in decode_report.get('truncatedIndexes',[]):truncated_images.append(chunk[local][0])
                elapsed=(time.perf_counter()-began)*1000;inference_ms+=elapsed
                decoded=self.base.vocab.batch_decode(ids.tolist())
                for (index,_,_),text,score in zip(chunk,decoded,prob.tolist()):texts[index]=text;scores[index]=score
                reports.append({'indexes':[i for i,_,_ in chunk],'batchSize':len(chunk),'tensorWidth':bucket,
                    'unpaddedWidths':[w for _,_,w in chunk],'inferenceMs':round(elapsed,3),'decode':decode_report})
        self.last_report={'policy':self.policy,'precision':self.precision,'preprocessor':self.normalizer.report(),
            'truncatedImageIndexes':truncated_images,'actualForwards':len(reports),'actualBatchSizes':[g['batchSize'] for g in reports],
            'groups':reports,'preprocessAndUploadMs':round(preprocess_ms,3),'inferenceAndDecodeMs':round(inference_ms,3),
            'wallMs':round((time.perf_counter()-start)*1000,3),'padded':self.policy=='bucket'}
        return (texts,scores) if return_prob else texts
