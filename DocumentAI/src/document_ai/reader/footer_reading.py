"""Bounded, opt-in template text-line retries; never infer missing date digits."""
import math
import re
from PIL import ImageOps
from document_ai.name_comparison import unaccent_name
from document_ai.adapters.ocr import crop_line
from .data import normalize_field


def parse_date_text(key,text):
    value=normalize_field(key,text)
    if value is not None:return value
    flat=unaccent_name(text)
    if key=='issueDate' and ('NGAY' in flat or 'DATE' in flat or ('THANG' in flat and 'NAM' in flat)):
        nums=re.findall(r'(?<![0-9])[0-9]{1,4}(?![0-9])',flat)
        if len(nums)==3 and len(nums[0])<=2 and len(nums[1])<=2 and len(nums[2])==4:
            return normalize_field(key,f'{int(nums[0]):02}/{int(nums[1]):02}/{nums[2]}')
    return None


def project_quad(spec,profile,size):
    w,h=size;m=float(profile['contextMarginFraction'])
    if not math.isfinite(m) or not 0<=m<=.2:raise ValueError('Invalid context margin')
    points=spec['polygon']
    if len(points)!=4:raise ValueError('Expected four direct reading points')
    cw=w/(1+2*m);ch=h/(1+2*m);result=[]
    for x,y in points:
        if not math.isfinite(x+y) or not -.1<=x<=1.1 or not -.1<=y<=1.1:
            raise ValueError('Invalid direct reading coordinates')
        px=(m+x)*cw;py=(m+y)*ch
        if not 0<=px<w or not 0<=py<h:raise ValueError('Direct reading outside image')
        result.append([px,py])
    # Validate geometry without reordering semantic TL/TR/BR/BL for the text line.
    import cv2
    import numpy as np
    polygon=np.asarray(result,dtype=np.float32)
    if not cv2.isContourConvex(polygon) or abs(cv2.contourArea(polygon))<20:
        raise ValueError('Invalid direct reading quadrilateral')
    if result[1][0]<=result[0][0] or result[3][1]<=result[0][1]:
        raise ValueError('Expected text-line TL/TR/BR/BL')
    return result


def direct_footer_read(image,key,profile,ocr,folder=None):
    spec=profile.get('directReadings',{}).get(key)
    if not spec:return {'status':'NOT_CONFIGURED','attempts':[],'candidate':None,'candidates':[]}
    if not hasattr(ocr,'recognize_line_crops'):
        return {'status':'NOT_SUPPORTED','attempts':[],'candidate':None,'candidates':[]}
    attempts=[]
    try:
        polygon=project_quad(spec,profile,image.size)
        original=crop_line(image,polygon)
        modes=spec.get('variants',['original','grayscale'])
        if not 1<=len(modes)<=3 or len(modes)!=len(set(modes)) or any(v not in {'original','grayscale','clahe'} for v in modes):
            raise ValueError('Invalid direct reading variants')
        crops=[]
        for mode in modes:
            if mode=='original':crop=original
            elif mode=='grayscale':crop=ImageOps.grayscale(original).convert('RGB')
            else:
                import cv2
                import numpy as np
                from PIL import Image
                gray=np.asarray(ImageOps.grayscale(original))
                crop=Image.fromarray(cv2.createCLAHE(clipLimit=2,tileGridSize=(8,8)).apply(gray)).convert('RGB')
            crops.append(crop)
            if folder:crop.save(folder/(key+'-direct-'+mode+'.png'))
        predictions=ocr.recognize_line_crops(crops)
        if len(predictions)!=len(crops):raise ValueError('Direct reading result count mismatch')
        candidates=[]
        for mode,line in zip(modes,predictions):
            value=parse_date_text(key,line['text'])
            candidate=({'value':value,'rawText':line['text'],'confidence':line.get('confidence'),
                'lineIndex':None,'side':'FRONT','polygon':polygon,'method':'TEMPLATE_DIRECT_LINE',
                'profile':profile['id'],'attempt':'direct_'+mode,
                'recognitionEngine':line.get('recognitionEngine')} if value is not None else None)
            if candidate:candidates.append(candidate)
            attempts.append({'variant':mode,'text':line['text'],'confidence':line.get('confidence'),
                'parsedValue':value,'recognitionEngine':line.get('recognitionEngine'),
                'recognitionStatus':line.get('status','OK'),'recognitionError':line.get('errorType'),
                'rawRecognitionResult':{k:line[k] for k in ('rawReturnedText','rawConfidence','singleRetryError','recoveredFrom') if k in line},
                'reason':None if value is not None else 'DATE_OR_UNLIMITED_NOT_READ'})
        values={c['value'] for c in candidates}
        # Agreement between variants is a pilot guard, not independent verification.
        selected=candidates[0] if len(values)==1 and len(candidates)>=2 else None
        return {'status':'EXTRACTED' if selected else 'AMBIGUOUS' if len(values)>1 else 'INCONCLUSIVE',
                'polygon':polygon,'attempts':attempts,'candidate':selected,'candidates':candidates,
                'policy':'TWO_VALID_VARIANTS_AGREE_NO_VALID_CONFLICT','validated':False}
    except Exception as exc:
        return {'status':'ERROR','attempts':attempts,'candidate':None,'candidates':[],
                'errorType':type(exc).__name__}
