import io
import warnings

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

from document_ai.core import Status, StepOutput


def decode(raw, limits):
    if not raw:
        return StepOutput(Status.FAIL,["EMPTY_FILE"])
    if len(raw)>limits.get("max_bytes",8*1024*1024):
        return StepOutput(Status.FAIL,["FILE_TOO_LARGE"])
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error",Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(raw)) as source:
                fmt=source.format
                if fmt not in {"JPEG","PNG"}:
                    return StepOutput(Status.FAIL,["UNSUPPORTED_FORMAT"])
                if getattr(source,"n_frames",1)!=1:
                    return StepOutput(Status.FAIL,["MULTIFRAME_NOT_SUPPORTED"])
                width,height=source.size
                if width*height>limits.get("max_pixels",20_000_000):
                    return StepOutput(Status.FAIL,["PIXEL_LIMIT_EXCEEDED"])
                source.verify()
            with Image.open(io.BytesIO(raw)) as source:
                image=ImageOps.exif_transpose(source).convert("RGB")
                image.load()
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError, Image.DecompressionBombWarning):
        return StepOutput(Status.FAIL,["INVALID_IMAGE"])
    return StepOutput(Status.PASS,metrics={"width":image.width,"height":image.height,"format":fmt,"bytes":len(raw)},payload={"image":image})


def quality(image,config,mask=None):
    import cv2
    region=config.get("roi")
    if region is not None:
        if len(region)!=4 or not all(isinstance(x,(int,float)) and np.isfinite(x) for x in region) or not (0<=region[0]<region[2]<=1 and 0<=region[1]<region[3]<=1):
            raise ValueError("Invalid normalized quality ROI")
        w,h=image.size
        image=image.crop((int(region[0]*w),int(region[1]*h),int(region[2]*w),int(region[3]*h)))
    gray=cv2.cvtColor(np.asarray(image),cv2.COLOR_RGB2GRAY)
    scale=min(1.0,1000/max(gray.shape))
    if scale<1:
        gray=cv2.resize(gray,None,fx=scale,fy=scale)
    if mask is not None:
        valid=cv2.resize(mask,(gray.shape[1],gray.shape[0]),interpolation=cv2.INTER_NEAREST)>0
        valid=cv2.erode(valid.astype(np.uint8),np.ones((5,5),np.uint8))>0
    else: valid=np.ones(gray.shape,dtype=bool)
    if not valid.any(): return StepOutput(Status.FAIL,["EMPTY_QUALITY_REGION"])
    blur=float(cv2.Laplacian(gray,cv2.CV_64F)[valid].var())
    dark=float((gray[valid]<25).mean())
    bright=float((gray[valid]>250).mean())
    reasons=[]
    if min(image.size)<config.get("min_short_side",400):reasons.append("RESOLUTION_TOO_LOW")
    if blur<config.get("min_laplacian_variance",60):reasons.append("BLUR")
    if dark>config.get("max_dark_fraction",0.65):reasons.append("TOO_DARK")
    if bright>config.get("max_bright_fraction",0.80):reasons.append("OVEREXPOSED")
    return StepOutput(Status.FAIL if reasons else Status.PASS,reasons,
        {"laplacian_variance":blur,"dark_fraction":dark,"bright_fraction":bright,
         "calibrated":config.get("calibrated",False),
         "measurement_scope":"card_polygon" if mask is not None else "configured_roi" if region is not None else "whole_image",
         "local_glare_detection":"NOT_IMPLEMENTED"})


def match_template(image,side,profile):
    import cv2
    from pathlib import Path
    spec=profile.get("templates",{}).get(side)
    if not spec or not spec.get("reference"):
        return StepOutput(Status.INCONCLUSIVE,["REFERENCE_NOT_CONFIGURED"])
    path=(Path(profile["_base"])/spec["reference"]).resolve()
    if not path.is_file():return StepOutput(Status.ERROR,["REFERENCE_NOT_FOUND"])
    try:
        with Image.open(path) as src:
            reference=ImageOps.exif_transpose(src).convert("RGB")
        if max(reference.size)>2000:
            reference.thumbnail((2000,2000))
        target=image.copy();target.thumbnail((2000,2000))
        orb=cv2.ORB_create(nfeatures=3000)
        kr,dr=orb.detectAndCompute(cv2.cvtColor(np.asarray(reference),cv2.COLOR_RGB2GRAY),None)
        kt,dt=orb.detectAndCompute(cv2.cvtColor(np.asarray(target),cv2.COLOR_RGB2GRAY),None)
        if dr is None or dt is None:return StepOutput(Status.INCONCLUSIVE,["INSUFFICIENT_FEATURES"])
        pairs=cv2.BFMatcher(cv2.NORM_HAMMING).knnMatch(dr,dt,k=2)
        good=[a for pair in pairs if len(pair)==2 for a,b in [pair] if a.distance<0.72*b.distance]
        if len(good)<12:return StepOutput(Status.INCONCLUSIVE,["TEMPLATE_NOT_CONFIRMED"],{"matches":len(good)})
        src=np.float32([kr[m.queryIdx].pt for m in good]).reshape(-1,1,2)
        dst=np.float32([kt[m.trainIdx].pt for m in good]).reshape(-1,1,2)
        h,mask=cv2.findHomography(src,dst,cv2.RANSAC,4.0)
        ratio=float(mask.mean()) if mask is not None else 0.0
        if h is None or ratio<spec.get("min_inlier_ratio",0.60):
            return StepOutput(Status.INCONCLUSIVE,["TEMPLATE_NOT_CONFIRMED"],{"inlier_ratio":ratio})
        w,rh=reference.size
        corners=np.float32([[0,0],[w-1,0],[w-1,rh-1],[0,rh-1]]).reshape(-1,1,2)
        quad=cv2.perspectiveTransform(corners,h).reshape(4,2)
        tw,th=target.size
        if not np.isfinite(quad).all() or not cv2.isContourConvex(quad.astype(np.float32)) or abs(cv2.contourArea(quad))<0.05*tw*th or (quad[:,0]<-4).any() or (quad[:,1]<-4).any() or (quad[:,0]>tw+4).any() or (quad[:,1]>th+4).any():
            return StepOutput(Status.FAIL,["DOCUMENT_CROPPED_OR_ALIGNMENT_INVALID"])
        # Match at bounded resolution, then warp original oriented pixels once.
        s=np.diag([tw/image.width,th/image.height,1.0])
        transform=np.linalg.inv(h)@s
        aligned=cv2.warpPerspective(np.asarray(image),transform,(w,rh))
        return StepOutput(Status.PASS,metrics={"matches":len(good),"inlier_ratio":ratio,
            "method":"reference_ORB_not_semantic_classifier"},payload={"image":Image.fromarray(aligned),
            "transform":transform.tolist()},implementation="opencv-orb/experimental-v1")
    except (OSError,ValueError,cv2.error,np.linalg.LinAlgError):
        return StepOutput(Status.ERROR,["TEMPLATE_PROCESSING_ERROR"])


def diagnostics(raw,image):
    import cv2
    from PIL import Image
    arr=np.asarray(image)
    small=image.copy();small.thumbnail((1200,1200))
    a=np.asarray(small)
    buf=io.BytesIO();small.save(buf,format="JPEG",quality=95)
    rec=np.asarray(Image.open(io.BytesIO(buf.getvalue())).convert("RGB"))
    diff=np.abs(a.astype(np.float32)-rec.astype(np.float32)).mean(axis=2)
    gray=cv2.cvtColor(a,cv2.COLOR_RGB2GRAY).astype(np.float32)
    power=np.abs(np.fft.fftshift(np.fft.fft2(gray-gray.mean())))**2
    yy,xx=np.indices(gray.shape);radius=np.sqrt(((yy-gray.shape[0]/2)/gray.shape[0])**2+((xx-gray.shape[1]/2)/gray.shape[1])**2)
    high=float(power[radius>0.25].sum()/max(float(power.sum()),1.0))
    view=np.clip(diff*8,0,255).astype(np.uint8)
    out=io.BytesIO();Image.fromarray(view).save(out,format="PNG")
    return StepOutput(Status.INCONCLUSIVE,["EXPERIMENTAL_METRICS_NOT_VERDICT"],
        {"ela_mean_abs_difference":float(diff.mean()),"ela_max_abs_difference":float(diff.max()),
         "jpeg_reencode_quality":95,"fft_high_frequency_energy_ratio":high,
         "ela_input":"bounded_oriented_derivative_not_unmodified_bytes"},
        evidence={"ela.png":out.getvalue()},implementation="ela-fft/diagnostic-v1")
