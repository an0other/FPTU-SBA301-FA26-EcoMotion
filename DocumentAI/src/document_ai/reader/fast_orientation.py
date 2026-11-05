"""Orientation classifier + bounded OCR verification, never full-resolution probes."""
import json,os
import math
import time
import numpy as np
from PIL import Image
from .orientation import CLUES,horizontal_support
from .document_titles import title_evidence
from document_ai.name_comparison import unaccent_name


def preview(image,max_side):
    if max_side<128:raise ValueError('Preview side too small')
    result=image.copy();result.thumbnail((max_side,max_side),Image.Resampling.LANCZOS)
    return result


def scale_lines(lines,source_size,target_size):
    import copy
    sx,sy=target_size[0]/source_size[0],target_size[1]/source_size[1]
    result=copy.deepcopy(lines)
    for line in result:
        if line.get('polygon'):line['polygon']=[[float(x)*sx,float(y)*sy] for x,y in line['polygon']]
    return result


class PaddleOrientation:
    def __init__(self,device='cpu',model=None):self.device=device;self.model=model;self.failed=False
    def predict(self,image):
        if self.failed:raise RuntimeError('Orientation model previously failed; restart to retry')
        if self.model is None:
            try:
                from paddleocr import DocImgOrientationClassification
                self.model=DocImgOrientationClassification(model_name='PP-LCNet_x1_0_doc_ori',
                    device=self.device,enable_mkldnn=False,enable_hpi=False)
            except Exception:
                self.failed=True;raise
        try:outputs=list(self.model.predict(input=np.asarray(image.convert('RGB'))[:,:,::-1].copy(),batch_size=1))
        except Exception:
            self.failed=True;raise
        if len(outputs)!=1:raise ValueError('Expected one orientation prediction')
        data=outputs[0].json
        if callable(data):data=data()
        if isinstance(data,str):data=json.loads(data)
        data=data.get('res',data)
        angle=int(data['label_names'][0]);score=float(data['scores'][0])
        if angle not in (0,90,180,270) or not math.isfinite(score) or not 0<=score<=1:
            raise ValueError('Invalid orientation prediction')
        return {'angle':angle,'confidence':score,'model':'PP-LCNet_x1_0_doc_ori'}


def clues(lines):
    text=' '.join(unaccent_name(l['text']) for l in lines if isinstance(l.get('text'),str))
    hits={name:weight for name,aliases,weight in CLUES if any(a in text for a in aliases)}
    title=title_evidence(lines)
    if title['documentType']:
        hits['gplx_title' if title['documentType']=='VN_DRIVING_LICENSE' else 'cccd_title']=4
    return hits


def orient(image,ocr,classifier,max_side=960,max_probes=4,card_rectified=False):
    small=preview(image,max_side);prediction=None;model_error=None
    classifier_started=time.perf_counter()
    if classifier is not None:
        try:prediction=classifier.predict(small)
        except Exception as error:model_error=type(error).__name__
    classifier_ms=round((time.perf_counter()-classifier_started)*1000,2)
    first=prediction['angle'] if prediction else 0
    angles=list(dict.fromkeys((first,(first+180)%360,0,90,180,270)))[:max_probes]
    exit_policy=os.getenv('DOCUMENT_AI_ORIENTATION_EXIT','reference')
    if exit_policy not in {'reference','text_verified'}:raise ValueError('Invalid orientation exit policy')
    attempts=[];runs=[];model_verified=False;exit_basis=None
    for angle in angles:
        work=small.rotate(angle,expand=True) if angle else small
        if card_rectified and work.width<=work.height*1.15:
            attempts.append({'rotationCounterClockwise':angle,'status':'SKIPPED','reason':'RECTIFIED_CARD_LANDSCAPE_REQUIRED'})
            continue
        probe_started=time.perf_counter()
        if getattr(ocr,'reuse_orientation_geometry',False):
            source=image.rotate(angle,expand=True) if angle else image
            ocr.bind_orientation_source(work,source)
        try:
            probe=getattr(ocr,'probe_orientation',None)
            passes=[]
            if probe:
                # Progressive geometry reuse; only unrecognized lines incur new recognition.
                for limit in getattr(ocr,'orientation_probe_limits',(6,12,None)):
                    lines=probe(work,limit);hits=clues(lines);score=sum(hits.values())
                    passes.append(dict(getattr(ocr,'last_timing',{})))
                    flat=''.join(''.join(unaccent_name(l.get('text') or '').split()) for l in lines)
                    strong='DACDIEMNHANDANG' in flat or ('LEFTINDEXFINGER' in flat and 'RIGHTINDEXFINGER' in flat)
                    if score>=4 or strong and score>=3:break
                    if limit is not None and len(lines)<limit:break
            else:lines=ocr.extract(work);hits=clues(lines);score=sum(hits.values())
            horizontal=horizontal_support(lines)
            attempts.append({'rotationCounterClockwise':angle,'status':'PASS','clues':list(hits),
                'evidenceWeight':score,'horizontalSupport':horizontal,'previewSize':list(work.size),'durationMs':round((time.perf_counter()-probe_started)*1000,2),
                'engineTimings':getattr(ocr,'last_timing',{}),'progressivePasses':passes})
            runs.append((score,angle,lines,work.size))
            # Fast acceptance needs both model confidence and independently observed text clues.
            flat=''.join(''.join(unaccent_name(l.get('text') or '').split()) for l in lines)
            strong_back='DACDIEMNHANDANG' in flat or ('LEFTINDEXFINGER' in flat and 'RIGHTINDEXFINGER' in flat)
            verified_score=score>=4 or strong_back and score>=3 and prediction and prediction['confidence']>=.90
            if prediction and angle==first and prediction['confidence']>=.85 and verified_score and horizontal is not None and horizontal>=.85:
                model_verified=True;exit_basis='CLASSIFIER_WITH_TEXT'
                break
            if exit_policy=='text_verified' and card_rectified and title_evidence(lines)['documentType'] and horizontal is not None and horizontal>=.90:
                model_verified=True;exit_basis='PRINTED_TITLE_AND_HORIZONTAL_TEXT_ON_RECTIFIED_CARD'
                break
        except Exception as error:
            if getattr(error,'fatal_for_request',False):raise
            attempts.append({'rotationCounterClockwise':angle,'status':'ERROR','exceptionType':type(error).__name__})
    ranked=sorted(runs,key=lambda r:r[0],reverse=True)
    resolved=bool(ranked and ranked[0][0]>=3 and (len(ranked)==1 or ranked[0][0]>ranked[1][0]))
    if not model_verified and any(a['status']=='ERROR' for a in attempts):resolved=False
    best=ranked[0] if resolved else None
    angle=best[1] if best else None
    full=image.rotate(angle,expand=True) if angle else image
    transfer={}
    if best and getattr(ocr,'reuse_orientation_geometry',False):
        chosen=small.rotate(angle,expand=True) if angle else small
        transfer=ocr.transfer_orientation_geometry(chosen,full)
    return {'orientationGeometry':transfer,'status':'RESOLVED' if resolved else 'INCONCLUSIVE','rotationApplied':angle,
        'image':full,'lines':scale_lines(best[2],best[3],full.size) if best else [],
        'attempts':attempts,'readyForOcr':resolved,'strategy':'CLASSIFIER_AND_BOUNDED_PREVIEW',
        'selectionBasis':'MODEL_WITH_TEXT_VERIFICATION' if prediction else 'BOUNDED_TEXT_FALLBACK',
        'classifier':prediction,'classifierError':model_error,'classifierMs':classifier_ms,'exitPolicy':exit_policy,'earlyAcceptanceBasis':exit_basis,
        'rectifiedLandscapePrior':card_rectified,'thresholdsCalibrated':False,
        'reason':None if resolved else 'ORIENTATION_NOT_RESOLVED'}


class ScaledDetector:
    def __init__(self,detector,max_side=1280):self.detector=detector;self.max_side=max_side
    def detect(self,image):
        small=preview(image,self.max_side)
        lines=self.detector.detect(small)
        self.last_timing={**getattr(self.detector,'last_timing',{}),'previewSize':list(small.size),'sourceSize':list(image.size)}
        return scale_lines(lines,small.size,image.size)

    def close(self):
        close=getattr(self.detector,'close',None)
        if close:close()

    def begin_request(self):
        begin=getattr(self.detector,'begin_request',None)
        if begin:begin()

    @property
    def last_failure(self):return getattr(self.detector,'last_failure',None)

    @property
    def is_ready(self):return getattr(self.detector,'is_ready',True)
