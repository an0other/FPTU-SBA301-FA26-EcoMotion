"""Bounded regional quality measurements, never a pass/fail or authenticity rule."""
import cv2
import numpy as np


def region_metrics(gray, rect):
    x,y,w,h=rect
    crop=gray[y:y+h,x:x+w]
    return {'laplacianVariance':float(cv2.Laplacian(crop,cv2.CV_64F).var()),
            'darkFraction':float((crop<25).mean()),'brightFraction':float((crop>250).mean())}


def measure_regions(image):
    gray=cv2.cvtColor(np.asarray(image.convert('RGB')),cv2.COLOR_RGB2GRAY)
    scale=min(1.,1000/max(gray.shape))
    if scale<1:gray=cv2.resize(gray,None,fx=scale,fy=scale,interpolation=cv2.INTER_AREA)
    sy,sx=image.height/gray.shape[0],image.width/gray.shape[1]
    regions=[]
    def append(kind,rect,**extra):
        x,y,w,h=map(int,rect)
        if w<4 or h<4:return
        regions.append({'kind':kind,'status':'MEASURED_NOT_CLASSIFIED',
            'bboxCardPixels':[round(x*sx),round(y*sy),round((x+w)*sx),round((y+h)*sy)],
            'bboxMeasurementPixels':[x,y,x+w,y+h],
            'metrics':region_metrics(gray,(x,y,w,h)),**extra})
    # Detection only: no payload decoding and no use of expected identity/type.
    qr_status='NOT_DETECTED'
    qr_error=None
    try:
        found,points=cv2.QRCodeDetector().detect(gray)
        if found and points is not None:
            points=np.asarray(points,dtype=float).reshape(-1,2)
            x1,y1=np.maximum(0,np.floor(points.min(axis=0))).astype(int)
            x2,y2=np.minimum([gray.shape[1],gray.shape[0]],np.ceil(points.max(axis=0))).astype(int)
            if x2-x1>=4 and y2-y1>=4:
                append('QR_CANDIDATE',(x1,y1,x2-x1,y2-y1),semanticRegionConfirmed=False)
                qr_status='CANDIDATE_DETECTED_NOT_DECODED'
    except cv2.error as error:
        qr_status='DETECTOR_ERROR';qr_error=type(error).__name__
    # Text-like edge groups are candidates only: holograms, patterns or QR may also match.
    gradient=cv2.morphologyEx(gray,cv2.MORPH_GRADIENT,cv2.getStructuringElement(cv2.MORPH_RECT,(3,3)))
    _,binary=cv2.threshold(gradient,0,255,cv2.THRESH_BINARY|cv2.THRESH_OTSU)
    proposals=[]
    for kernel in ((17,3),(3,17)):
        grouped=cv2.morphologyEx(binary,cv2.MORPH_CLOSE,cv2.getStructuringElement(cv2.MORPH_RECT,kernel))
        contours,_=cv2.findContours(grouped,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            rect=cv2.boundingRect(contour);x,y,w,h=rect
            if min(w,h)<5 or max(w,h)<25 or not 1.7<=max(w,h)/min(w,h)<=40:continue
            if not 100<=w*h<=gray.size*.20:continue
            if any(abs(x-a)<5 and abs(y-b)<5 and abs(w-c)<8 and abs(h-d)<8 for a,b,c,d in proposals):continue
            proposals.append(rect)
    for rect in sorted(proposals,key=lambda r:r[2]*r[3],reverse=True)[:12]:
        append('TEXT_LIKE_CANDIDATE',rect,semanticRegionConfirmed=False)
    return {'implementation':'opencv-regional-quality/diagnostic-v1','status':'DIAGNOSTIC_ONLY',
        'decisionUsed':False,'thresholdsCalibrated':False,'cardImageSize':list(image.size),
        'measurementImageSize':[gray.shape[1],gray.shape[0]],
        'coordinates':'card.png before orientation','qrCandidateStatus':qr_status,
        'qrDetectorError':qr_error,'regions':regions,
        'limitations':['Candidate boxes are not verified text/QR regions',
                      'Local brightness is not a glare or tampering verdict',
                      'Metrics depend on scale, compression and resampling; no regional rejection threshold']}


def save_overlay(image,report,path):
    from PIL import ImageDraw
    canvas=image.copy();draw=ImageDraw.Draw(canvas)
    for index,region in enumerate(report['regions']):
        rect=region['bboxCardPixels'];color='blue' if region['kind']=='QR_CANDIDATE' else 'orange'
        draw.rectangle(rect,outline=color,width=3);draw.text((rect[0],max(0,rect[1]-12)),str(index),fill=color)
    canvas.save(path)
