"""Original-resolution QR recovery with bounded, traceable retries.
No model-generated pixels, payload guessing or navigation to decoded URLs.
"""
import io
import time
import cv2
import numpy as np
from PIL import Image, ImageDraw
from document_ai.core import Status, StepOutput


def native_box(points, scale, shape):
    """Map detector coordinates from preview to the original source pixels."""
    points=np.asarray(points,dtype=np.float32).reshape(-1,2)/scale
    if not np.isfinite(points).all():raise ValueError('Nonfinite QR coordinates')
    lo=points.min(axis=0);hi=points.max(axis=0)
    pad=max(8,int(max(hi-lo)*.18))
    h,w=shape[:2]
    return (max(0,int(np.floor(lo[0]))-pad),max(0,int(np.floor(lo[1]))-pad),
            min(w,int(np.ceil(hi[0]))+pad+1),min(h,int(np.ceil(hi[1]))+pad+1))


def validate_roi(roi):
    if len(roi)!=4 or not all(isinstance(x,(int,float)) and np.isfinite(x) for x in roi) or not(0<=roi[0]<roi[2]<=1 and 0<=roi[1]<roi[3]<=1):
        raise ValueError('QR ROI must be normalized x1,y1,x2,y2')


def scan_qr(image, config=None, alternate_image=None, save_evidence=False):
    config=config or {}
    fast_source=bool(config.get('stop_after_source_success',False))
    started=time.perf_counter()
    deadline=started+min(20.,max(.1,float(config.get('time_budget_seconds',6))))
    cap=min(48,max(1,int(config.get('max_attempts',28))))
    sources={'original':cv2.cvtColor(np.asarray(image.convert('RGB')),cv2.COLOR_RGB2GRAY)}
    if alternate_image is not None and alternate_image is not image:
        sources['aligned']=cv2.cvtColor(np.asarray(alternate_image.convert('RGB')),cv2.COLOR_RGB2GRAY)
    attempts=[];values=set();detected=False;proposals=[];seen_boxes=set();evidence={}
    try:
        import zxingcpp
    except ImportError:zxingcpp=None
    engines=(['zxingcpp'] if zxingcpp is not None else [])+['opencv']
    detector=cv2.QRCodeDetector()

    def available():return len(attempts)<cap and time.perf_counter()<deadline

    def attempt(source,label,pixels,scale=None):
        nonlocal detected
        for engine in engines:
            if not available():return
            before=time.perf_counter();count=0;error=None;points=None
            try:
                if engine=='zxingcpp':
                    hits=zxingcpp.read_barcodes(pixels,formats=zxingcpp.BarcodeFormat.QRCode)
                    detected=detected or bool(hits)
                    for hit in hits:
                        if hit.valid and hit.text:values.add(hit.text);count+=1
                else:
                    ok,texts,points,_=detector.detectAndDecodeMulti(pixels)
                    if points is not None:detected=True
                    for text in texts or ():
                        if text:values.add(text);count+=1
                    if count==0:
                        text,single,_=detector.detectAndDecode(pixels)
                        if single is not None:
                            detected=True
                            if points is None:points=single
                        if text:values.add(text);count+=1
                if points is not None and scale is not None:
                    for quad in np.asarray(points).reshape(-1,4,2)[:4]:
                        box=native_box(quad,scale,sources[source].shape)
                        if (source,box) not in seen_boxes:
                            seen_boxes.add((source,box));proposals.append((source,box))
            except Exception as exc:error=type(exc).__name__
            attempts.append({'source':source,'variant':label,'engine':engine,
                'width':pixels.shape[1],'height':pixels.shape[0],
                'decoded_count':count,'error_type':error,
                'duration_ms':round((time.perf_counter()-before)*1000,2)})
            if count and fast_source:break

    # Full previews first on BOTH sources, to expose competing decoded values.
    for source,gray in sources.items():
        factor=min(1.,1600/max(gray.shape))
        preview=cv2.resize(gray,None,fx=factor,fy=factor) if factor<1 else gray
        attempt(source,'full_preview',preview,factor)
    candidates=[]
    for source,box in proposals:
        x1,y1,x2,y2=box;crop=sources[source][y1:y2,x1:x2]
        if crop.size:candidates.append((source,'detected_native',crop,box))
    if config.get('roi') is not None:
        roi=config['roi'];validate_roi(roi)
        source=config.get('roi_source','original')
        if source not in sources:raise ValueError('QR roi_source unavailable')
        gray=sources[source];h,w=gray.shape
        box=(int(roi[0]*w),int(roi[1]*h),int(roi[2]*w),int(roi[3]*h))
        x1,y1,x2,y2=box
        candidates.insert(0,(source,'configured_native',gray[y1:y2,x1:x2],box))
    if save_evidence:
        for source,gray in sources.items():
            overlay=Image.fromarray(gray).convert('RGB');draw=ImageDraw.Draw(overlay)
            for s,box in proposals:
                if s==source:draw.rectangle(box,outline='red',width=3)
            stream=io.BytesIO();overlay.thumbnail((1600,1600));overlay.save(stream,format='PNG')
            evidence[f'qr-{source}-overlay.png']=stream.getvalue()
    if not values:
        # Native resolution decodes BEFORE resampling, then prioritized native crops.
        for source,gray in sources.items():
            if values or not available():break
            if max(gray.shape)>1600:attempt(source,'full_native',gray,1.)
        # Full-native calls may discover more native proposals.
        for source,box in proposals:
            if any(s==source and b==box for s,_,_,b in candidates):continue
            x1,y1,x2,y2=box;crop=sources[source][y1:y2,x1:x2]
            if crop.size:candidates.append((source,'detected_native',crop,box))
        # Overlapping original-resolution tiles: no prior hardcoded QR location.
        for source,gray in sources.items():
            h,w=gray.shape
            for y0,y1 in ((0,.65),(.35,1)):
                for x0,x1 in ((0,.65),(.35,1)):
                    box=(int(x0*w),int(y0*h),int(x1*w),int(y1*h))
                    a,b,c,d=box
                    candidates.append((source,f'tile_{x0}_{y0}',gray[b:d,a:c],box))
        prepared=[]
        # Try all native candidates first, so early tiles cannot exhaust every retry.
        for index,(source,label,crop,box) in enumerate(candidates):
            if values or not available():break
            if max(crop.shape)>2400:
                factor=2400/max(crop.shape);crop=cv2.resize(crop,None,fx=factor,fy=factor)
                label+='_bounded'
            prepared.append((source,label,crop))
            if save_evidence and index<8:
                stream=io.BytesIO();Image.fromarray(crop).save(stream,format='PNG')
                evidence[f'qr-candidate-{index:02d}.png']=stream.getvalue()
            attempt(source,label,crop)
        for source,label,crop in prepared:
            if values or not available():break
            border=cv2.copyMakeBorder(crop,24,24,24,24,cv2.BORDER_CONSTANT,value=255)
            attempt(source,label+'_border',border)
            if values:break
            factor=min(3.,1600/max(border.shape))
            scaled=cv2.resize(border,None,fx=factor,fy=factor,interpolation=cv2.INTER_CUBIC)
            attempt(source,label+'_scaled',scaled)
            if values:break
            _,binary=cv2.threshold(scaled,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
            attempt(source,label+'_otsu',binary)
    decoded=sorted(values)
    completed=sum(x['error_type'] is None for x in attempts)
    if len(decoded)>1:status,reason=Status.INCONCLUSIVE,'MULTIPLE_QR_PAYLOADS'
    elif len(decoded)==1:status,reason=Status.PASS,None
    elif not completed:status,reason=Status.ERROR,'QR_DECODER_ERROR'
    else:status,reason=Status.INCONCLUSIVE,'QR_DETECTED_NOT_DECODED' if detected else 'QR_NOT_DETECTED'
    return StepOutput(status,[] if reason is None else [reason],
        {'decoded_count':len(decoded),'detected':detected,'attempts':attempts,
         'available_engines':engines,'fallback_available':zxingcpp is not None,
         'sources':{s:{'width':a.shape[1],'height':a.shape[0]} for s,a in sources.items()},
         'native_candidate_boxes':[{'source':s,'box':list(b)} for s,b in proposals],
         'successful_attempts':[{'source':a['source'],'variant':a['variant'],'engine':a['engine']} for a in attempts if a['decoded_count']],
         'budget_exhausted':not available(),
         'budget_note':'Soft shared budget between native calls; not a hard timeout',
         'search_complete':False,'decoderStrategy':'FIRST_SUCCESS_PER_SOURCE' if fast_source else 'ALL_AVAILABLE_ENGINES_PER_PREVIEW','authenticity_verified':False},payload={'values':decoded},
        evidence=evidence,implementation='qr/original-and-aligned-v2')
