"""Recognition adapters. Disagreement is evidence of uncertainty, never fraud."""
import json
import math
import os
import unicodedata
import numpy as np
from PIL import Image


def canonical(text):
    # Preserve accents, digits and punctuation. No guessed spelling correction.
    return " ".join(unicodedata.normalize("NFC", text).upper().split())


def crop_line(image, polygon, source_pixels=None):
    import cv2
    points = np.asarray(polygon, dtype=np.float32)
    if points.shape != (4, 2) or not np.isfinite(points).all():
        raise ValueError("Expected finite quadrilateral")
    # Paddle polygons: top-left, top-right, bottom-right, bottom-left.
    w = int(round(max(np.linalg.norm(points[1]-points[0]), np.linalg.norm(points[2]-points[3]))))
    h = int(round(max(np.linalg.norm(points[3]-points[0]), np.linalg.norm(points[2]-points[1]))))
    if w < 2 or h < 2 or w*h > image.width*image.height*2:
        raise ValueError("Invalid line crop")
    target = np.float32([[0,0],[w-1,0],[w-1,h-1],[0,h-1]])
    matrix = cv2.getPerspectiveTransform(points,target)
    pixels = cv2.warpPerspective(source_pixels if source_pixels is not None else np.asarray(image.convert("RGB")),matrix,(w,h),borderMode=cv2.BORDER_REPLICATE)
    return Image.fromarray(pixels)


class PaddleOcrEngine:
    implementation = "paddleocr/PP-OCRv5-vi"

    def __init__(self, device="cpu", version="PP-OCRv5", lang="vi", detection_model=None, recognition_model=None):
        self.version, self.lang = version, lang
        self.detection_model, self.recognition_model = detection_model, recognition_model
        self.implementation = f"paddleocr/{version}/{recognition_model or lang}"
        self.device = device
        self._model = None

    def extract(self, image):
        import time
        started=time.perf_counter();was_cold=self._model is None
        if self._model is None:
            from paddleocr import PaddleOCR
            kwargs = {}
            if self.detection_model: kwargs["text_detection_model_name"] = self.detection_model
            if self.recognition_model: kwargs["text_recognition_model_name"] = self.recognition_model
            if not (self.detection_model and self.recognition_model):
                kwargs.update(lang=self.lang,ocr_version=self.version)
            self._model = PaddleOCR(device=self.device,
                enable_mkldnn=False, enable_hpi=False,
                use_doc_orientation_classify=False, use_doc_unwarping=False,
                use_textline_orientation=False, **kwargs)
        initialized=time.perf_counter()
        lines = []
        for result in self._model.predict(np.asarray(image.convert("RGB"))[:,:,::-1].copy()):
            data = result.json
            if callable(data): data = data()
            if isinstance(data,str): data = json.loads(data)
            data = data.get("res",data)
            texts, scores, polygons = (data.get(k,[]) for k in ("rec_texts","rec_scores","rec_polys"))
            if not (len(texts)==len(scores)==len(polygons)):
                raise ValueError("Inconsistent OCR result lengths")
            for text, score, polygon in zip(texts,scores,polygons):
                score = float(score)
                if not math.isfinite(score) or not 0 <= score <= 1:
                    raise ValueError("Invalid OCR confidence")
                lines.append({"text":str(text),"confidence":score,
                    "polygon":np.asarray(polygon).tolist(),"recognition_status":"SINGLE_ENGINE_UNVALIDATED"})
        self.last_timing={'initializationMs':round((initialized-started)*1000,2),
            'inferenceAndParseMs':round((time.perf_counter()-initialized)*1000,2),'coldModel':was_cold}
        return lines


class VietOcrRecognizer:
    def __init__(self, device="cpu", model="vgg_transformer", weights=None):
        self.device, self.model, self.weights = device, model, weights
        self._predictor = None

    def _ensure_predictor(self):
        if self._predictor is None:
            from vietocr.tool.config import Cfg
            from vietocr.tool.predictor import Predictor
            config = Cfg.load_config_from_name(self.model)
            config["device"] = self.device
            config["cnn"]["pretrained"] = False
            config["predictor"]["beamsearch"] = False
            if self.weights: config["weights"] = self.weights
            self._predictor = Predictor(config)
            policy=os.getenv('DOCUMENT_AI_BATCH_POLICY','legacy')
            decoder=os.getenv('DOCUMENT_AI_DECODE_BACKEND','legacy')
            precision=os.getenv('DOCUMENT_AI_PRECISION','fp32')
            preprocessor=os.getenv('DOCUMENT_AI_PREPROCESSOR','numpy')
            if policy not in {'legacy','exact','bucket'}:raise ValueError('Invalid batch policy')
            if decoder not in {'legacy','device'}:raise ValueError('Invalid decode backend')
            if policy!='legacy':
                from importlib.metadata import version
                if version('vietocr')!='0.3.13':raise RuntimeError('Experimental predictor currently supports vietocr 0.3.13 only; use legacy policy for other versions')
                from document_ai.reader.experimental_predictor import ExperimentalPredictor
                self._predictor=ExperimentalPredictor(self._predictor,policy,decoder,precision,preprocessor,
                    bucket_width=int(os.getenv('DOCUMENT_AI_BUCKET_WIDTH','64')),
                    max_batch=int(os.getenv('DOCUMENT_AI_BATCH_SIZE','8')))
            elif decoder!='legacy' or precision!='fp32' or preprocessor!='numpy':
                raise ValueError('Custom decoder/precision/preprocessing requires batch policy exact or bucket')
    def recognize(self, image):
        self._ensure_predictor()
        text, probability = self._predictor.predict(image.convert("RGB"), return_prob=True)
        probability = float(probability)
        if not math.isfinite(probability) or not 0 <= probability <= 1:
            raise ValueError("Invalid recognizer confidence")
        return str(text), probability


    def recognize_batch(self,images,batch_size=4,tolerate_invalid=False,max_single_retries=4):
        import time
        if not 1<=batch_size<=32:raise ValueError('batch_size must be 1..32')
        self._ensure_predictor()
        if not 0<=max_single_retries<=8:raise ValueError("Invalid single retry budget")
        retries=0
        if not hasattr(self._predictor,'predict_batch'):
            raise RuntimeError('This VietOCR has no predict_batch; choose batch_size=1')
        indexed=sorted(enumerate(images),key=lambda v:v[1].width/max(1,v[1].height))
        scheduler=os.getenv('DOCUMENT_AI_BATCH_SCHEDULER','legacy')
        if scheduler not in {'legacy','global'}:raise ValueError('Invalid batch scheduler')
        chunks=[]
        if scheduler=='global':
            from document_ai.reader.experimental_predictor import resized_width
            base=getattr(self._predictor,'base',self._predictor)
            dataset=base.config['dataset'];buckets={}
            for index,image in indexed:
                width=resized_width(image.width,image.height,dataset)
                if getattr(self._predictor,'policy','exact')=='bucket':
                    width=min(dataset['image_max_width'],math.ceil(width/self._predictor.bucket_width)*self._predictor.bucket_width)
                buckets.setdefault(width,[]).append((index,image))
            for width,items in sorted(buckets.items()):
                chunks.extend(items[i:i+batch_size] for i in range(0,len(items),batch_size))
        else:chunks=[indexed[i:i+batch_size] for i in range(0,len(indexed),batch_size)]
        output=[None]*len(images);groups=[]
        for chunk in chunks:
            start=time.perf_counter()
            actual=[];hook=None
            model=getattr(getattr(self._predictor,'base',self._predictor),'model',None)
            cnn=getattr(model,'cnn',None)
            if hasattr(cnn,'register_forward_pre_hook'):
                def capture(module,inputs):
                    tensor=inputs[0];actual.append({'batchSize':int(tensor.shape[0]),'height':int(tensor.shape[2]),'width':int(tensor.shape[3])})
                hook=cnn.register_forward_pre_hook(capture)
            try:texts,scores=self._predictor.predict_batch([im.convert('RGB') for _,im in chunk],return_prob=True)
            finally:
                if hook:hook.remove()
            if len(texts)!=len(chunk) or len(scores)!=len(chunk):raise ValueError('Batch output length mismatch')
            duration=round((time.perf_counter()-start)*1000,3)
            truncated_local=getattr(self._predictor,'last_report',{}).get('truncatedImageIndexes',[])
            truncated={chunk[i][0] for i in truncated_local}
            for (index,_),text,score in zip(chunk,texts,scores):
                try:score=float(score)
                except (TypeError,ValueError):score=float('nan')
                valid=math.isfinite(score) and 0<=score<=1 and index not in truncated
                if not valid and not tolerate_invalid:raise ValueError('Invalid batch confidence')
                row={'text':str(text),'confidence':score if valid else None,'elapsedMs':None,
                     'batchGroup':len(groups),'batchGroupMs':duration,'status':'OK' if valid else 'ERROR'}
                if not valid:
                    row.update(rawReturnedText=str(text),rawConfidence=str(score),errorType='DECODE_TRUNCATED' if index in truncated else 'INVALID_BATCH_CONFIDENCE')
                    # Invalid confidence cannot enter field extraction as recognized text.
                    row['text']=''
                    if index not in truncated and retries<max_single_retries:
                        retries+=1
                        try:
                            retry_text,retry_score=self.recognize(images[index])
                            row.update(text=retry_text,confidence=retry_score,status='OK',recoveredFrom='INVALID_BATCH_CONFIDENCE')
                        except (ValueError,FloatingPointError) as error:
                            row['singleRetryError']=type(error).__name__
                output[index]=row
            groups.append({'indexes':[i for i,_ in chunk],'durationMs':duration,
                'observedEncoderForwards':actual,'actualForwardCount':len(actual) if hook else None,
                'scheduler':scheduler,'experiment':getattr(self._predictor,'last_report',{})})
        self.last_single_retry_count=retries
        self.last_batch_groups=groups
        return output


class HybridOcrEngine:
    implementation = "paddleocr-vietocr/agreement-v1"

    def __init__(self, detector, recognizer):
        self.detector, self.recognizer = detector, recognizer

    def extract(self, image):
        lines = self.detector.extract(image)
        for line in lines:
            original = line["text"]
            line["candidates"] = [{"engine":"paddleocr","text":original,"confidence":line["confidence"]}]
            try:
                text, score = self.recognizer.recognize(crop_line(image,line["polygon"]))
                line["candidates"].append({"engine":"vietocr","text":text,"confidence":score})
                agreed = bool(canonical(original)) and canonical(original)==canonical(text)
                line["recognition_status"] = "AGREEMENT" if agreed else "DISAGREEMENT"
                # Keep original, do not choose by incomparable confidence scores.
            except Exception as exc:
                line["recognition_status"] = "SECONDARY_ERROR"
                line["secondary_error_type"] = type(exc).__name__
        return lines


def create_ocr(config):
    primary = PaddleOcrEngine(config.get("device","cpu"),config.get("version","PP-OCRv5"),
        config.get("lang","vi"),config.get("detection_model"),config.get("recognition_model"))
    engine = config.get("engine","paddle")
    if engine == "paddle": return primary
    if engine != "hybrid": raise ValueError("Unsupported OCR engine")
    return HybridOcrEngine(primary, VietOcrRecognizer(config.get("secondary_device","cpu"),
        config.get("secondary_model","vgg_transformer"),config.get("secondary_weights")))
