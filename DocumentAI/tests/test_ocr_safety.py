import numpy as np
from PIL import Image
from document_ai.adapters.ocr import HybridOcrEngine, crop_line, canonical
from document_ai.extraction import extract_fields, compare
from document_ai.ocr_evidence import evidence_for_lines
from document_ai.imaging import quality

class Detector:
    def extract(self,image):
        return [{"text":"VŨ TIẾN ĐƠN","confidence":0.99,"polygon":[[0,0],[199,0],[199,39],[0,39]]}]
class Different:
    def recognize(self,image): return "VU TIEN DON",0.999
class Same:
    def recognize(self,image): return "vũ tiến đơn",0.91
class Broken:
    def recognize(self,image): raise RuntimeError("sensitive content")

def setup(recognizer):
    image=Image.new("RGB",(200,80),"white")
    lines=HybridOcrEngine(Detector(),recognizer).extract(image)
    profile={"fields":[{"name":"name","side":"FRONT","roi":[0,0,1,1],"required":True,"require_agreement":True}]}
    return image,lines,profile

def test_disagreement_not_resolved_by_higher_confidence():
    image,lines,profile=setup(Different())
    assert lines[0]["recognition_status"]=="DISAGREEMENT"
    assert lines[0]["text"]=="VŨ TIẾN ĐƠN"
    fields=extract_fields({"FRONT":lines},{"FRONT":image},profile)
    assert not fields[0]["reliable"]
    assert compare(fields,{"name":"OTHER"},profile)[0]["status"]=="INCONCLUSIVE"

def test_agreement_preserves_accents():
    image,lines,profile=setup(Same())
    assert extract_fields({"FRONT":lines},{"FRONT":image},profile)[0]["reliable"]
    assert canonical("ĐƠN")!=canonical("DON")

def test_secondary_error_does_not_silently_pass():
    image,lines,profile=setup(Broken())
    assert lines[0]["recognition_status"]=="SECONDARY_ERROR"
    assert "sensitive content" not in str(lines)
    assert not extract_fields({"FRONT":lines},{"FRONT":image},profile)[0]["reliable"]

def test_crop_evidence_and_no_invalid_geometry():
    image,lines,_=setup(Same())
    assert crop_line(image,lines[0]["polygon"]).size==(199,39)
    result=evidence_for_lines(image,lines)
    assert "ocr-line-000.png" in result and "ocr-overlay.png" in result

def test_identifier_fullmatch_blocks_truncated_number():
    image=Image.new("RGB",(200,80))
    lines=[{"text":"12345","confidence":0.999,"polygon":[[0,0],[100,0],[100,30],[0,30]]}]
    p={"fields":[{"name":"number","side":"FRONT","roi":[0,0,1,1],"validation_pattern":"[0-9]{12}"}]}
    assert not extract_fields({"FRONT":lines},{"FRONT":image},p)[0]["reliable"]

def test_quality_roi_excludes_busy_background():
    pixels=np.random.default_rng(8).integers(0,255,(600,1000,3),dtype=np.uint8)
    pixels[100:500,200:800]=128
    image=Image.fromarray(pixels)
    assert quality(image,{}).status=="PASS"
    result=quality(image,{"roi":[.2,1/6,.8,5/6]})
    assert result.status=="FAIL" and "BLUR" in result.reasons
