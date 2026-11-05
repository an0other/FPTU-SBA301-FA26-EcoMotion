"""Contour baseline, not a trained document classifier."""
import cv2
import numpy as np
from document_ai.geometry import rectify
from .corner_order import order_corners


def locate(image,source_hash):
    scale=min(1.,1200/max(image.size));w,h=[max(2,int(s*scale)) for s in image.size]
    gray=cv2.cvtColor(np.asarray(image.resize((w,h))),cv2.COLOR_RGB2GRAY)
    gray=cv2.GaussianBlur(gray,(5,5),0)
    edges=cv2.Canny(gray,40,120)
    edges=cv2.morphologyEx(edges,cv2.MORPH_CLOSE,np.ones((5,5),np.uint8))
    _,threshold=cv2.threshold(gray,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    candidates=[]
    for mask in (edges,threshold,255-threshold):
        contours,_=cv2.findContours(mask,cv2.RETR_LIST,cv2.CHAIN_APPROX_SIMPLE)
        for contour in sorted(contours,key=cv2.contourArea,reverse=True)[:80]:
            area=cv2.contourArea(contour)/(w*h)
            if not .05<=area<=.95:continue
            p=cv2.approxPolyDP(contour,.02*cv2.arcLength(contour,True),True)
            if len(p)!=4 or not cv2.isContourConvex(p):continue
            pts=p[:,0,:].astype(float)
            center=pts.mean(axis=0);pts=pts[np.argsort(np.arctan2(pts[:,1]-center[1],pts[:,0]-center[0]))]
            pts=order_corners(pts)
            lengths=[np.linalg.norm(pts[(i+1)%4]-pts[i]) for i in range(4)]
            width=max(lengths[0],lengths[2]);height=max(lengths[1],lengths[3])
            ratio=max(width,height)/max(1,min(width,height))
            if not 1.25<=ratio<=2.05:continue
            if (pts[:,0]<2).any() or (pts[:,0]>=w-2).any() or (pts[:,1]<2).any() or (pts[:,1]>=h-2).any():continue
            box=[pts[:,0].min(),pts[:,1].min(),pts[:,0].max(),pts[:,1].max()]
            duplicate=False
            for old in candidates:
                b=old['box'];inter=max(0,min(box[2],b[2])-max(box[0],b[0]))*max(0,min(box[3],b[3])-max(box[1],b[1]))
                union=(box[2]-box[0])*(box[3]-box[1])+(b[2]-b[0])*(b[3]-b[1])-inter
                if union and inter/union>.8:duplicate=True;break
            if duplicate:continue
            candidates.append({'points':pts/scale,'box':box,'rank':area*(1-abs(ratio-1.586)/2),'area_fraction':area})
    candidates.sort(key=lambda c:c['rank'],reverse=True)
    if not candidates:return {'status':'NOT_FOUND','reason':'CARD_NOT_LOCATED','candidates':[]}
    if len(candidates)>1 and candidates[1]['rank']>=candidates[0]['rank']*.8:
        return {'status':'AMBIGUOUS','reason':'MULTIPLE_CARD_CANDIDATES','candidates':[{'corners':c['points'].tolist(),'area_fraction':c['area_fraction']} for c in candidates[:4]]}
    winner=candidates[0]
    result=rectify(image,{'sha256':source_hash,'corners':winner['points'].tolist()},source_hash)
    if result.status!='PASS':return {'status':'NOT_FOUND','reason':'GEOMETRY_REJECTED','candidates':[]}
    return {'status':'LOCATED','method':'opencv-contour/v1','semantic_type_verified':False,
            'corners':winner['points'].tolist(),'image':result.payload['image'],'homography':result.payload['transform']}
