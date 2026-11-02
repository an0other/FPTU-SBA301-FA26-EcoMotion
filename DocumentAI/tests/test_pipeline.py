import io
import json
from pathlib import Path

import numpy as np
from PIL import Image
from fastapi.testclient import TestClient

from document_ai.core import InputImage, load_profile
from document_ai.pipeline.runner import Pipeline
from document_ai.imaging import decode,match_template
from document_ai.extraction import compare, extract_fields
from document_ai.api import create_app

ROOT=Path(__file__).resolve().parents[1]

def picture():
    a=np.random.default_rng(7).integers(30,220,(500,800,3),dtype=np.uint8)
    b=io.BytesIO();Image.fromarray(a).save(b,format="PNG");return b.getvalue()

def profile():return load_profile(ROOT/"configs/gplx.example.json")

class NeverOcr:
    def extract(self,image):raise AssertionError("OCR should not be reached")

class FixtureOcr:
    # Only dependency-injected tests: never used as runtime fallback.
    def extract(self,image):return [{"text":"TEST-123","confidence":0.99,"polygon":[[1,1],[200,1],[200,80],[1,80]]}]


def test_invalid_file_stops_ocr(tmp_path):
    r=Pipeline(profile(),tmp_path,NeverOcr()).run([InputImage("FRONT",b"not-an-image","front")])
    assert r["assessment"]=="INPUT_UNSUITABLE"
    assert next(x for x in r["checks"] if x["check"]=="OCR")["status"]=="SKIPPED"


def test_quality_failure_stops(tmp_path):
    b=io.BytesIO();Image.new("RGB",(800,500),"white").save(b,format="PNG")
    r=Pipeline(profile(),tmp_path,NeverOcr()).run([InputImage("FRONT",b.getvalue(),"front")])
    assert any(x["check"]=="QUALITY" and x["status"]=="FAIL" for x in r["checks"])


def test_missing_template_never_clear(tmp_path):
    p=profile();p["required_sides"]=["FRONT"]
    r=Pipeline(p,tmp_path,NeverOcr()).run([InputImage("FRONT",picture(),"front")])
    assert r["assessment"]=="INCONCLUSIVE"
    assert next(x for x in r["checks"] if x["check"]=="DOCUMENT_TYPE")["status"]=="INCONCLUSIVE"


def test_diagnostic_can_exercise_parser_without_claiming_clear(tmp_path):
    p=profile();p["required_sides"]=["FRONT"]
    p["fields"]=[{"name":"number","side":"FRONT","roi":[0,0,1,1],"kind":"identifier","required":True}]
    r=Pipeline(p,tmp_path,FixtureOcr()).run([InputImage("FRONT",picture(),"front")],{"number":"TEST123"},True)
    assert r["extractedData"][0]["value"]=="TEST123"
    assert r["comparisons"][0]["status"]=="PASS"
    assert r["assessment"]!="CLEAR"
    assert (tmp_path/r["requestId"]/"report.json").is_file()


def test_ocr_failure_is_error_not_fraud(tmp_path):
    p=profile();p["required_sides"]=["FRONT"]
    r=Pipeline(p,tmp_path,NeverOcr()).run([InputImage("FRONT",picture(),"front")],diagnostic=True)
    assert r["processingStatus"]=="PARTIAL"
    assert r["assessment"]=="INCONCLUSIVE"


def test_pixel_limit_checked_before_decode():
    o=decode(picture(),{"max_pixels":10})
    assert o.reasons==["PIXEL_LIMIT_EXCEEDED"]


def test_orb_alignment_real_computation(tmp_path):
    from PIL import ImageDraw
    im=Image.open(io.BytesIO(picture()))
    path=tmp_path/"reference.png";im.save(path)
    p=profile();p["_base"]=str(tmp_path);p["templates"]["FRONT"]["reference"]="reference.png"
    r=match_template(im,"FRONT",p)
    assert r.status=="PASS"
    assert r.payload["image"].size==im.size


def test_cross_side_mismatch():
    p={"comparisons":[{"id":"number-pair","left":{"name":"number","side":"FRONT","source":"OCR"},"right":{"name":"number","side":"BACK","source":"OCR"}}]}
    fs=[{"name":"number","side":s,"source":"OCR","value":v,"reliable":True} for s,v in [("FRONT","A"),("BACK","B")]]
    assert compare(fs,{},p)[0]["status"]=="FAIL"
    fs[1]["reliable"]=False
    assert compare(fs,{},p)[0]["status"]=="INCONCLUSIVE"


def test_api_validation_and_auth(tmp_path,monkeypatch):
    monkeypatch.setenv("DOCUMENT_AI_API_KEY","test-key")
    c=TestClient(create_app(ROOT/"configs/gplx.example.json",tmp_path,NeverOcr()))
    assert c.get("/health").status_code==200
    assert c.get("/v1/capabilities").status_code==401
    assert c.get("/v1/capabilities",headers={"X-API-Key":"test-key"}).status_code==200
    r=c.post("/v1/analyses",headers={"X-API-Key":"test-key"},data={"document_type":"VN_DRIVING_LICENSE"},files={"front":("x.png",b"bad","image/png")})
    assert r.status_code==200
    assert r.json()["assessment"]=="INPUT_UNSUITABLE"
    r=c.post("/v1/analyses",headers={"X-API-Key":"test-key"},data={"document_type":"WRONG"},files={"front":("x.png",b"bad","image/png")})
    assert r.status_code==422


def test_required_forensics_stops_before_ocr(tmp_path,monkeypatch):
    from document_ai.core import StepOutput,Status
    from document_ai.pipeline import runner
    p=profile();p['required_sides']=['FRONT'];p['forensics']={'mode':'required','thresholds_validated':True}
    monkeypatch.setattr(runner,'match_template',lambda image,side,p:StepOutput(Status.PASS,payload={'image':image,'transform':[[1,0,0],[0,1,0],[0,0,1]]}))
    monkeypatch.setattr(runner.TruForAnalyzer,'analyze',lambda *args:StepOutput(Status.INCONCLUSIVE,['GRAY_ZONE']))
    r=Pipeline(p,tmp_path,NeverOcr()).run([InputImage('FRONT',picture(),'front')])
    assert next(c for c in r['checks'] if c['check']=='OCR')['status']=='SKIPPED'
    assert r['assessment']=='INCONCLUSIVE'


def test_disabled_forensics_strict_cannot_pass_to_ocr(tmp_path,monkeypatch):
    from document_ai.core import StepOutput,Status
    from document_ai.pipeline import runner
    p=profile();p['required_sides']=['FRONT']
    monkeypatch.setattr(runner,'match_template',lambda image,side,p:StepOutput(Status.PASS,payload={'image':image,'transform':[[1,0,0],[0,1,0],[0,0,1]]}))
    r=Pipeline(p,tmp_path,NeverOcr()).run([InputImage('FRONT',picture(),'front')])
    assert next(c for c in r['checks'] if c['check']=='OCR')['status']=='SKIPPED'
    assert r['assessment']!='CLEAR'


def test_trufor_missing_command_not_pass():
    from document_ai.adapters.trufor import TruForAnalyzer
    result=TruForAnalyzer().analyze(b'',None,{})
    assert result.status=='ERROR'


def test_missing_side_remains_input_issue_in_diagnostics(tmp_path):
    r=Pipeline(profile(),tmp_path,FixtureOcr()).run([InputImage('FRONT',picture(),'front')],diagnostic=True)
    assert r['assessment']=='INPUT_UNSUITABLE'
