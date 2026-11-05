import pytest
from PIL import Image,ImageDraw
from document_ai.reader.text_regions import PaddleTextRegions
from document_ai.reader.vietocr_pipeline import DetectorOnlyVietOcrPipeline
from document_ai.reader.orientation import resolve_orientation


def test_detection_never_invents_text_or_recognition_confidence():
    class Result:
        json={'res':{'dt_polys':[[[1,1],[100,1],[100,25],[1,25]]],'dt_scores':[.91]}}
    class Model:
        def predict(self,**kwargs):return [Result()]
    region=PaddleTextRegions(model=Model()).detect(Image.new('RGB',(150,50)))[0]
    assert region['text'] is None and region['confidence'] is None
    assert region['detectionConfidence']==.91
    assert region['primaryRecognition']=='NOT_RUN'


def test_full_card_and_roi_do_not_call_paddle_recognizer():
    class Primary:
        def extract(self,image):raise AssertionError('Not needed for body recognition')
    class Detector:
        calls=0
        def detect(self,image):
            self.calls+=1
            return [{'polygon':[[1,1],[100,1],[100,25],[1,25]],'text':None,
                     'confidence':None,'detectionConfidence':.91}]
    class Recognizer:
        model='vgg_transformer'
        def recognize_many(self,images):return [{'text':'NGUYỄN AN','confidence':.95} for image in images]
    detector=Detector();engine=DetectorOnlyVietOcrPipeline(Primary(),Recognizer(),detector)
    for action in [engine.read_oriented,engine.extract]:
        lines=action(Image.new('RGB',(150,50)))
        assert lines[0]['text']=='NGUYỄN AN' and lines[0]['primaryText'] is None
        assert lines[0]['detectionConfidence']==.91
    assert detector.calls==2


def test_detector_error_is_not_hidden_by_old_recognition():
    class Detector:
        def detect(self,image):raise RuntimeError('detection failed')
    class Recognizer:model='vgg_transformer'
    engine=DetectorOnlyVietOcrPipeline(object(),Recognizer(),Detector())
    with pytest.raises(RuntimeError):engine.extract(Image.new('RGB',(150,50)))


@pytest.mark.parametrize('rotation',[0,90,180,270])
def test_header_orientation_preserves_full_size_and_rotation(rotation):
    image=Image.new('RGB',(160,100),'black');ImageDraw.Draw(image).rectangle((0,0,20,20),fill='red')
    class Ocr:
        shapes=[]
        def extract(self,image):
            self.shapes.append(image.size)
            return [{'text':"DRIVER'S LICENSE"}] if image.getpixel((0,0))==(255,0,0) else []
    ocr=Ocr();result=resolve_orientation(image.rotate(rotation,expand=True),ocr,
        card_rectified=True,fast=True,header_probe=True)
    assert result['status']=='RESOLVED'
    assert result['rotationApplied']==(-rotation)%360
    assert result['image'].size==(160,100)
    assert result['strategy']=='HEADER_40_PERCENT'
    assert ocr.shapes==[(160,40),(160,40)]


def test_uncertain_header_falls_back_to_full_image():
    image=Image.new('RGB',(160,100),'black');ImageDraw.Draw(image).rectangle((0,0,20,20),fill='red')
    class Ocr:
        calls=0
        def extract(self,work):
            self.calls+=1
            return [{'text':"DRIVER'S LICENSE"}] if work.height==100 and work.getpixel((0,0))==(255,0,0) else []
    ocr=Ocr();result=resolve_orientation(image,ocr,card_rectified=True,fast=True,header_probe=True)
    assert result['status']=='RESOLVED' and result['strategy']=='FULL_IMAGE_FALLBACK'
    assert len(result['headerProbeAttempts'])==4
    assert ocr.calls==4
