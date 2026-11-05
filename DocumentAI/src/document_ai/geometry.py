"""Manual geometry baseline, bound to immutable input bytes; not classification."""
import io
import cv2
import numpy as np
from PIL import Image
from document_ai.core import Status, StepOutput


def rectify(image, selection, expected_hash):
    if selection.get("sha256") != expected_hash:
        return StepOutput(Status.FAIL,["STALE_CORNER_SELECTION"])
    points=np.asarray(selection.get("corners",[]),dtype=np.float32)
    if points.shape!=(4,2) or not np.isfinite(points).all():
        return StepOutput(Status.FAIL,["INVALID_CORNERS"])
    w,h=image.size
    if (points[:,0]<0).any() or (points[:,1]<0).any() or (points[:,0]>=w).any() or (points[:,1]>=h).any():
        return StepOutput(Status.FAIL,["CORNERS_OUTSIDE_IMAGE"])
    if not cv2.isContourConvex(points) or cv2.contourArea(points,oriented=True)<=0:
        return StepOutput(Status.FAIL,["CORNERS_MUST_BE_TL_TR_BR_BL"])
    # Reject degenerate selection; do not stretch a tiny patch to a full card.
    if cv2.contourArea(points)<.01*w*h:
        return StepOutput(Status.FAIL,["CARD_REGION_TOO_SMALL"])
    width=int(round(max(np.linalg.norm(points[1]-points[0]),np.linalg.norm(points[2]-points[3]))))
    height=int(round(max(np.linalg.norm(points[3]-points[0]),np.linalg.norm(points[2]-points[1]))))
    if min(width,height)<50 or max(width,height)/min(width,height)>4:
        return StepOutput(Status.FAIL,["DEGENERATE_CARD_GEOMETRY"])
    scale=min(1.,2000/max(width,height))
    ow,oh=max(2,int(width*scale)),max(2,int(height*scale))
    target=np.float32([[0,0],[ow-1,0],[ow-1,oh-1],[0,oh-1]])
    matrix=cv2.getPerspectiveTransform(points,target)
    array=np.asarray(image.convert("RGB"))
    aligned=Image.fromarray(cv2.warpPerspective(array,matrix,(ow,oh)))
    mask=np.zeros((h,w),np.uint8);cv2.fillConvexPoly(mask,points.astype(np.int32),255)
    stream=io.BytesIO();aligned.save(stream,format="PNG")
    return StepOutput(Status.PASS,metrics={"method":"manual_four_corners","source_width":width,
        "source_height":height,"semantic_type_verified":False},
        payload={"image":aligned,"transform":matrix.tolist(),"mask":mask},
        evidence={"aligned.png":stream.getvalue()},implementation="geometry/manual-v1")
