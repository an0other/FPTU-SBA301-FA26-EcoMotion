"""Order-independent mask localization. New weights must be trained and evaluated."""
import hashlib
import json
from pathlib import Path
import cv2
import numpy as np
from document_ai.geometry import rectify
from .corner_order import order_corners


def quad_from_polygon(polygon, width, height):
    pts = np.asarray(polygon, dtype=np.float32)
    if pts.ndim != 2 or pts.shape[1] != 2 or len(pts) < 4 or not np.isfinite(pts).all():
        raise ValueError('INVALID_MASK_POLYGON')
    if (pts < 0).any() or (pts[:, 0] >= width).any() or (pts[:, 1] >= height).any():
        raise ValueError('MASK_OUTSIDE_IMAGE')
    hull = cv2.convexHull(pts)
    perimeter = cv2.arcLength(hull, True)
    candidates = []
    # Never substitute minAreaRect: that cannot recover perspective corners.
    for epsilon in (.005, .01, .015, .02, .025, .03):
        approx = cv2.approxPolyDP(hull, epsilon * perimeter, True).reshape(-1, 2)
        if len(approx) != 4:
            continue
        quad = order_corners(approx)
        # Compute local-mask IoU at bounded resolution.
        scale = min(1., 1000 / max(width, height))
        shape = (max(2, round(height*scale)), max(2, round(width*scale)))
        a = np.zeros(shape, np.uint8); b = a.copy()
        cv2.fillPoly(a, [np.rint(pts*scale).astype(np.int32)], 1)
        cv2.fillConvexPoly(b, np.rint(quad*scale).astype(np.int32), 1)
        union = np.count_nonzero(a | b)
        iou = np.count_nonzero(a & b) / max(1, union)
        lengths = np.linalg.norm(np.roll(quad, -1, axis=0)-quad, axis=1)
        width_est = max(lengths[0], lengths[2]); height_est = max(lengths[1], lengths[3])
        ratio = max(width_est, height_est) / max(1, min(width_est, height_est))
        fraction = abs(cv2.contourArea(quad.astype(np.float32))) / (width*height)
        if 1.2 <= ratio <= 2.1 and .03 <= fraction <= .98 and iou >= .93:
            candidates.append((iou, quad))
    if not candidates:
        raise ValueError('MASK_NOT_CARD_QUADRILATERAL')
    return max(candidates, key=lambda item: item[0])


class SegmentationLocator:
    def __init__(self, weights, device='cpu', threshold=.5, model=None):
        self.weights = Path(weights).resolve()
        meta = json.loads(self.weights.with_suffix('.card.json').read_text(encoding='utf-8'))
        if meta.get('task') != 'card-segmentation/v1':
            raise ValueError('Expected new segmentation weights, not old pose checkpoint')
        if hashlib.sha256(self.weights.read_bytes()).hexdigest() != meta['sha256']:
            raise ValueError('Checkpoint hash mismatch')
        self.device, self.threshold, self.model = device, threshold, model

    def __call__(self, image, source_hash):
        if self.model is None:
            from ultralytics import YOLO
            self.model = YOLO(str(self.weights), task='segment')
        if self.model.names != {0: 'document_card'}:
            raise ValueError('Unexpected model classes')
        result = self.model.predict(source=image, imgsz=640, rect=False, device=self.device,
                                    conf=self.threshold, verbose=False)[0]
        if result.boxes is None or len(result.boxes) == 0 or result.masks is None:
            return {'status': 'NOT_FOUND', 'reason': 'CARD_MASK_NOT_FOUND'}
        candidates=list(result.masks.xy)
        overlap_meta=None
        if len(result.boxes) != 1 or len(candidates) != 1:
            # Keep explicit geometry evidence. Do not choose the biggest card.
            quads=[]
            try:
                if len(candidates)>16 or len(result.boxes)!=len(candidates):raise ValueError('Unbounded/unequal masks')
                quads=[quad_from_polygon(p,*image.size) for p in candidates]
            except ValueError:
                return {'status':'AMBIGUOUS','reason':'MULTIPLE_CARD_MASKS','candidateCount':len(candidates)}
            overlaps=[]
            for i,(_,a) in enumerate(quads):
                for _,b in quads[i+1:]:
                    intersection,_=cv2.intersectConvexConvex(a.astype(np.float32),b.astype(np.float32))
                    union=abs(cv2.contourArea(a.astype(np.float32)))+abs(cv2.contourArea(b.astype(np.float32)))-intersection
                    overlaps.append(float(intersection/max(union,1)))
            if not overlaps or min(overlaps)<.97:
                return {'status':'AMBIGUOUS','reason':'MULTIPLE_CARD_MASKS','candidateCount':len(candidates),
                        'candidateCorners':[q.tolist() for _,q in quads],'pairwiseQuadIoU':overlaps}
            # Strict same-footprint duplicates only; threshold remains a pilot heuristic.
            best=max(range(len(quads)),key=lambda i:quads[i][0]);candidates=[candidates[best]]
            overlap_meta={'originalCount':len(quads),'minimumPairwiseQuadIoU':min(overlaps),
                          'selectedBy':'MASK_QUAD_FIT','thresholdCalibrated':False}
        try:
            iou, corners = quad_from_polygon(candidates[0], *image.size)
        except ValueError as error:
            return {'status': 'NOT_FOUND', 'reason': str(error)}
        g = rectify(image, {'sha256': source_hash, 'corners': corners.tolist()}, source_hash)
        if g.status != 'PASS':
            return {'status': 'NOT_FOUND', 'reason': 'MASK_GEOMETRY_REJECTED'}
        # Sample real surrounding source pixels. This is context, NOT a claim that
        # predicted corners are exact. Never resize/stretch the cropped raster.
        aligned=g.payload['image']; ow,oh=aligned.size
        mx,my=max(1,round(ow*.06)),max(1,round(oh*.06))
        matrix=np.array([[1.,0.,mx],[0.,1.,my],[0.,0.,1.]]) @ np.asarray(g.payload['transform'])
        from PIL import Image
        aligned=Image.fromarray(cv2.warpPerspective(np.asarray(image.convert('RGB')),matrix,(ow+2*mx,oh+2*my),borderMode=cv2.BORDER_CONSTANT))
        return {'status': 'LOCATED', 'method': 'yolo-segmentation-quad/v1',
                'corners': corners.tolist(), 'image': aligned,
                'homography': matrix.tolist(), 'maskQuadIoU': iou,'cropContextMarginFraction': .06,
                'cropContextCorners':cv2.perspectiveTransform(np.float32([[[0,0],[ow+2*mx-1,0],[ow+2*mx-1,oh+2*my-1],[0,oh+2*my-1]]]),np.linalg.inv(matrix))[0].tolist(),
                'duplicateMaskReduction':overlap_meta,'semantic_type_verified': False}
