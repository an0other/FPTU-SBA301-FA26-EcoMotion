import json
import pytest
from PIL import Image,ImageDraw
from document_ai.reader.orientation import resolve_orientation
from document_ai.reader.field_regions import region_extract


def card():
    image=Image.new('RGB',(160,100),'black');ImageDraw.Draw(image).rectangle((0,0,20,20),fill='red');return image


class CountingOcr:
    def __init__(self):self.calls=0
    def extract(self,image):
        self.calls+=1
        return [{'text':"DRIVER'S LICENSE"},{'text':'Full name:'}] if image.getpixel((0,0))==(255,0,0) else [{'text':'background'}]


@pytest.mark.parametrize('rotation',[0,90,180,270])
def test_balanced_skips_only_angles_standard_already_excludes(rotation):
    image=card().rotate(rotation,expand=True)
    standard=CountingOcr();balanced=CountingOcr()
    a=resolve_orientation(image,standard,card_rectified=True)
    b=resolve_orientation(image,balanced,card_rectified=True,fast=True)
    assert a['status']==b['status']=='RESOLVED'
    assert a['rotationApplied']==b['rotationApplied']
    assert a['lines']==b['lines']
    assert standard.calls==4 and balanced.calls==2
    assert len([v for v in b['attempts'] if v['status']=='SKIPPED'])==2


def test_unrectified_image_still_requires_four_probes():
    ocr=CountingOcr()
    assert resolve_orientation(card(),ocr,fast=True)['status']=='RESOLVED'
    assert ocr.calls==4


def test_balanced_error_on_one_eligible_probe_never_claims_resolved():
    class Flaky(CountingOcr):
        def extract(self,image):
            if self.calls==1:raise RuntimeError('failure')
            return super().extract(image)
    assert resolve_orientation(card(),Flaky(),card_rectified=True,fast=True)['status']=='INCONCLUSIVE'


def profile(tmp_path):
    path=tmp_path/'p.json';path.write_text(json.dumps({'id':'pilot','documentType':'VN_DRIVING_LICENSE',
        'contextMarginFraction':0,'fields':{'fullName':[0,0,1,1]}}));return path


def line(text,confidence):return {'text':text,'confidence':confidence,'polygon':[[5,5],[150,5],[150,30],[5,30]]}


def test_selective_skips_retry_but_reports_it(tmp_path):
    class Ocr:
        calls=0
        def extract(self,image):self.calls+=1;return [line('NGUYEN AN',.95)]
    ocr=Ocr()
    r=region_extract([line('NGUYEN AN',.95)],'VN_DRIVING_LICENSE',Image.new('RGB',(200,100)),profile(tmp_path),ocr,retry_policy='selective')
    assert ocr.calls==0
    assert r['fullName']['retryStatus']=='SKIPPED_VALID_PRIMARY'
    assert r['fullName']['candidate']['value']=='NGUYEN AN'


def test_selective_still_retries_low_confidence_and_preserves_conflict(tmp_path):
    class Ocr:
        calls=0
        def extract(self,image):self.calls+=1;return [line('TRAN BINH',.95)]
    ocr=Ocr()
    r=region_extract([line('NGUYEN AN',.85)],'VN_DRIVING_LICENSE',Image.new('RGB',(200,100)),profile(tmp_path),ocr,retry_policy='selective')
    assert ocr.calls==1
    assert r['fullName']['status']=='AMBIGUOUS'
    assert r['fullName']['candidate'] is None


def test_no_eligible_card_orientation_is_inconclusive_not_model_error():
    ocr=CountingOcr()
    result=resolve_orientation(Image.new('RGB',(100,100)),ocr,card_rectified=True,fast=True)
    assert result['status']=='INCONCLUSIVE' and ocr.calls==0
