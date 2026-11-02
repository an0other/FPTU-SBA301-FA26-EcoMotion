"""Direct text-line recognition and strict Unicode transcription metrics."""
import json
import math
import unicodedata
import numpy as np

MODELS={'paddle-v5-latin':'latin_PP-OCRv5_mobile_rec',
        'paddle-v6-tiny':'PP-OCRv6_tiny_rec','paddle-v6-small':'PP-OCRv6_small_rec'}


def transcription_metrics(expected,actual):
    a=unicodedata.normalize('NFC',expected)
    b=unicodedata.normalize('NFC',actual)
    row=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        new=[i]
        for j,y in enumerate(b,1):new.append(min(new[-1]+1,row[j]+1,row[j-1]+(x!=y)))
        row=new
    return {'exact_match':a==b,'edit_distance':row[-1],
            'reference_characters':len(a),'cer':row[-1]/len(a) if a else None,
            'normalization':'NFC only; accents, case, punctuation and spaces retained'}


class PaddleLineRecognizer:
    def __init__(self,model_name,device='cpu'):
        self.model_name=model_name;self.device=device;self._model=None

    def recognize(self,image):
        if self._model is None:
            from paddleocr import TextRecognition
            self._model=TextRecognition(model_name=self.model_name,device=self.device,
                enable_mkldnn=False,enable_hpi=False)
        array=np.asarray(image.convert('RGB'))[:,:,::-1].copy()
        results=list(self._model.predict(input=array,batch_size=1))
        if len(results)!=1:raise ValueError('Expected one text-line result')
        data=results[0].json
        if callable(data):data=data()
        if isinstance(data,str):data=json.loads(data)
        data=data.get('res',data)
        text=str(data['rec_text']);score=float(data['rec_score'])
        if not math.isfinite(score) or not 0<=score<=1:raise ValueError('Invalid confidence')
        return text,score


def create_line_recognizer(engine,device='cpu',weights=None):
    if engine in MODELS:return PaddleLineRecognizer(MODELS[engine],device)
    if engine in {'vietocr-transformer','vietocr-seq2seq'}:
        from document_ai.adapters.ocr import VietOcrRecognizer
        return VietOcrRecognizer(device,'vgg_transformer' if engine.endswith('transformer') else 'vgg_seq2seq',weights)
    raise ValueError('Unknown recognizer')
