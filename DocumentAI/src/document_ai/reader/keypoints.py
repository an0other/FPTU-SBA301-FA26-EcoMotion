"""Optional fine-tuned four-keypoint locator. Never uses COCO human pose as a card detector."""
import hashlib,json
from pathlib import Path
import numpy as np
from document_ai.geometry import rectify
from .corner_order import order_corners


def validate_points(points,width,height):
    pts=np.asarray(points,dtype=float)
    if pts.shape!=(4,2) or not np.isfinite(pts).all():raise ValueError('Four finite corners required')
    if (pts[:,0]<0).any() or (pts[:,0]>=width).any() or (pts[:,1]<0).any() or (pts[:,1]>=height).any():raise ValueError('Corners outside image')
    # Validate ordered TL, TR, BR, BL through the existing geometry implementation.
    return pts


class KeypointLocator:
    def __init__(self,weights,device='cpu',threshold=.5):
        self.weights=Path(weights).resolve()
        if not self.weights.is_file():raise ValueError('Corner checkpoint missing')
        meta=json.loads(self.weights.with_suffix('.card.json').read_text(encoding='utf-8'))
        if meta.get('task')!='card-four-corners/v2' or meta.get('order')!='TOPMOST_CLOCKWISE':raise ValueError('Unsupported corner checkpoint contract')
        if hashlib.sha256(self.weights.read_bytes()).hexdigest()!=meta['sha256']:raise ValueError('Checkpoint metadata hash mismatch')
        self.device=device;self.threshold=threshold;self.model=None

    def __call__(self,image,source_hash):
        if self.model is None:
            from ultralytics import YOLO
            self.model=YOLO(str(self.weights),task='pose')
            if self.model.names!={0:'document_card'}:raise ValueError('Not a document-card model')
        # PIL RGB input: Ultralytics handles color conversion internally.
        result=self.model.predict(source=image,device=self.device,imgsz=640,conf=self.threshold,verbose=False)[0]
        if result.keypoints is None or result.boxes is None or not len(result.boxes):return {'status':'NOT_FOUND','reason':'CARD_NOT_LOCATED','method':'yolo-four-keypoints/v1'}
        if len(result.boxes)!=1:return {'status':'AMBIGUOUS','reason':'MULTIPLE_CARD_CANDIDATES','method':'yolo-four-keypoints/v1'}
        points=result.keypoints.xy[0].cpu().numpy()
        scores=result.keypoints.conf
        if scores is None or len(scores[0])!=4 or float(scores[0].min())<self.threshold:return {'status':'NOT_FOUND','reason':'CORNER_CONFIDENCE_LOW','method':'yolo-four-keypoints/v1'}
        try:points=order_corners(validate_points(points,*image.size))
        except ValueError:return {'status':'NOT_FOUND','reason':'INVALID_MODEL_CORNERS','method':'yolo-four-keypoints/v1'}
        rectified=rectify(image,{'sha256':source_hash,'corners':points.tolist()},source_hash)
        if rectified.status!='PASS':return {'status':'NOT_FOUND','reason':'MODEL_GEOMETRY_REJECTED','method':'yolo-four-keypoints/v1'}
        return {'status':'LOCATED','method':'yolo-four-keypoints/v1','corners':points.tolist(),'image':rectified.payload['image'],'homography':rectified.payload['transform'],'semantic_type_verified':False}
