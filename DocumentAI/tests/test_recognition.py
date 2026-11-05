import json
import sys
import types
import numpy as np
from PIL import Image
from document_ai.recognition import PaddleLineRecognizer,transcription_metrics


def test_accent_loss_not_normalized_away():
 assert not transcription_metrics('VŨ TIẾN ĐƠN','VU TIÉN DON')['exact_match']
 assert transcription_metrics('VŨ TIẾN ĐƠN','VU TIÉN DON')['cer']>0
 assert transcription_metrics('ĐƠN','ĐƠN')['cer']==0


def test_unicode_nfc_equivalence():
 import unicodedata
 text='TIẾN'
 assert transcription_metrics(text,unicodedata.normalize('NFD',text))['exact_match']


def test_direct_recognizer_does_not_detect_or_recrop(monkeypatch):
 seen={}
 class Result:
  json={'res':{'rec_text':'TEST','rec_score':.9}}
 class Model:
  def __init__(self,**kwargs):seen['config']=kwargs
  def predict(self,input,batch_size):
   seen['shape']=input.shape;seen['pixel']=input[0,0].tolist()
   return [Result()]
 monkeypatch.setitem(sys.modules,'paddleocr',types.SimpleNamespace(TextRecognition=Model))
 image=Image.new('RGB',(542,101),(10,20,30))
 result=PaddleLineRecognizer('MODEL').recognize(image)
 assert result==('TEST',.9)
 assert seen['shape']==(101,542,3) and seen['pixel']==[30,20,10]
 assert seen['config']['enable_mkldnn'] is False
