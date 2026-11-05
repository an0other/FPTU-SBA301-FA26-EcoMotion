import os
import hashlib
import io
import json
import re
from importlib.metadata import version, PackageNotFoundError
import time
import uuid
from dataclasses import asdict
from pathlib import Path

from document_ai.core import Check, Status, StepOutput, dumps
from document_ai.imaging import decode, quality, match_template, diagnostics
from document_ai.extraction import extract_fields, read_qr, qr_fields, compare
from document_ai.geometry import rectify
from document_ai.adapters.qr import scan_qr
from document_ai.adapters.ocr import create_ocr
from document_ai.ocr_evidence import evidence_for_lines
from document_ai.adapters.trufor import TruForAnalyzer


class Pipeline:
    def __init__(self,profile,output_root="runs",ocr=None):
        self.profile=profile
        self.output_root=Path(output_root)
        self.ocr=ocr or create_ocr(profile.get("ocr",{}))

    def run(self,inputs,declared=None,diagnostic=False,save_ocr_evidence=False,corners=None):
        declared=declared or {}
        if not isinstance(declared,dict) or not all(isinstance(k,str) and isinstance(v,str) for k,v in declared.items()):
            raise ValueError("Declared data must be strings")
        if not inputs or any(not re.fullmatch(r"[a-zA-Z0-9_-]{1,64}",x.image_id) for x in inputs) or len({x.image_id for x in inputs})!=len(inputs):
            raise ValueError("Unique safe image ids required")
        p=self.profile;start=time.perf_counter();rid=uuid.uuid4().hex
        folder=self.output_root/rid;folder.mkdir(parents=True,exist_ok=False)
        checks=[];lines={};aligned={};qr_values={};transformations=[]
        seen=[x.side for x in inputs]
        if len(seen)!=len(set(seen)) or any(x not in {"FRONT","BACK"} for x in seen):
            raise ValueError("Unique FRONT/BACK inputs required")

        def add(name,required,image_id,fn):
            before=time.perf_counter()
            if os.getenv("DOCUMENT_AI_PROGRESS")=="1":print(f"[{image_id or 'pair'}] {name} starting",flush=True)
            try:o=fn()
            except Exception as ex:
                # Do not expose exception text: SDKs can include paths or personal data.
                o=StepOutput(Status.ERROR,["STEP_EXECUTION_ERROR"],{"exception_type":type(ex).__name__})
            refs=[]
            for namepart,data in o.evidence.items():
                filename=f"{image_id or 'pair'}-{namepart}"
                (folder/filename).write_bytes(data);refs.append(filename)
            c=Check(name,o.status,required,image_id,o.reasons,o.metrics,refs,
                round((time.perf_counter()-before)*1000,2),o.implementation)
            checks.append(c)
            if os.getenv("DOCUMENT_AI_PROGRESS")=="1":print(f"[{image_id or 'pair'}] {name}: {o.status} ({c.duration_ms:.0f} ms)",flush=True)
            return o

        for missing in set(p["required_sides"])-set(seen):
            checks.append(Check("INPUT",Status.FAIL,True,missing,["REQUIRED_SIDE_MISSING"]))
        for inp in inputs:
            active=True
            def skip(name,required=True):
                checks.append(Check(name,Status.SKIPPED,required,inp.image_id,["PREREQUISITE_NOT_PASSED"]))
            dec=add("FILE",True,inp.image_id,lambda:decode(inp.content,p.get("limits",{})))
            if dec.status!=Status.PASS:
                for name in ("QUALITY","DOCUMENT_TYPE","IMAGE_FORENSICS","OCR","QR"):skip(name)
                continue
            image=dec.payload["image"]
            selection=(corners or {}).get(inp.side)
            geometry=None
            if selection is not None:
                geometry=add("GEOMETRY",True,inp.image_id,lambda:rectify(image,selection,inp.sha256))
                if geometry.status!=Status.PASS:
                    for name in ("QUALITY","DOCUMENT_TYPE","IMAGE_FORENSICS","OCR","QR"):skip(name)
                    continue
            q=add("QUALITY",True,inp.image_id,lambda:quality(image,dict(p.get("quality",{})) if geometry else {**p.get("quality",{}),**p.get("quality_by_side",{}).get(inp.side,{})},geometry.payload["mask"] if geometry else None))
            if q.status!=Status.PASS:
                for name in ("DOCUMENT_TYPE","IMAGE_FORENSICS","OCR","QR"):skip(name)
                continue
            if diagnostic:
                checks.append(Check("DOCUMENT_TYPE",Status.SKIPPED,True,inp.image_id,["DIAGNOSTIC_BYPASS"]))
                work=geometry.payload["image"] if geometry else image
                if geometry:
                    transformations.append({"image_id":inp.image_id,"from":"exif_oriented_original",
                        "to":"manual_aligned","homography":geometry.payload["transform"],
                        "width":work.width,"height":work.height})
            else:
                t=add("DOCUMENT_TYPE",True,inp.image_id,lambda:match_template(image,inp.side,p))
                active=t.status==Status.PASS
                work=t.payload.get("image",image)
                if active:
                    transformations.append({"image_id":inp.image_id,"from":"exif_oriented_original",
                        "to":"aligned","homography":t.payload["transform"],"width":work.width,"height":work.height})
            if active and geometry and diagnostic:
                aq=add("ALIGNED_QUALITY",True,inp.image_id,lambda:quality(work,p.get("quality",{})))
                active=aq.status==Status.PASS
            if not active:
                for name in ("IMAGE_FORENSICS","OCR","QR"):skip(name)
                continue
            fconfig=p.get("forensics",{});mode=fconfig.get("mode","disabled")
            if mode=="disabled":
                checks.append(Check("IMAGE_FORENSICS",Status.SKIPPED,True,inp.image_id,["FORENSICS_NOT_CONFIGURED"]))
                active=diagnostic
            else:
                f=add("IMAGE_FORENSICS",mode=="required",inp.image_id,
                    lambda:TruForAnalyzer().analyze(inp.content,image,fconfig))
                active=mode!="required" or f.status==Status.PASS
            if p.get("diagnostic_metrics",False):
                add("EXPERIMENTAL_ELA_FFT",False,inp.image_id,lambda:diagnostics(inp.content,image))
            if not active:
                skip("OCR");skip("QR");continue
            aligned[inp.side]=work
            def ocr_step():
                tokens=self.ocr.extract(work)
                lines[inp.side]=tokens
                uncertain = any(x.get("recognition_status") in {"DISAGREEMENT","SECONDARY_ERROR"} for x in tokens)
                return StepOutput(Status.INCONCLUSIVE if uncertain or not tokens else Status.PASS,
                    ["RECOGNIZER_UNCERTAINTY"] if uncertain else [] if tokens else ["NO_TEXT_READ"],
                    {"line_count":len(tokens),"meaning":"Execution result; not verified transcription accuracy",
                     "recognition_validation":"MULTI_ENGINE" if tokens and all("candidates" in x for x in tokens) else "NOT_EVALUATED",
                     "uncertain_line_count":sum(x.get("recognition_status") in {"DISAGREEMENT","SECONDARY_ERROR"} for x in tokens) if tokens and all("candidates" in x for x in tokens) else None},
                    evidence=evidence_for_lines(work,tokens) if save_ocr_evidence else {},
                    implementation=getattr(self.ocr,"implementation","injected-engine"))
            add("OCR",True,inp.image_id,ocr_step)
            qrconf=p.get("qr",{}).get(inp.side)
            if not qrconf or not qrconf.get("enabled",False):
                checks.append(Check("QR",Status.NOT_APPLICABLE,False,inp.image_id,["QR_NOT_APPLICABLE_FOR_PROFILE"]))
            else:
                def qr_step():
                    result=scan_qr(image,qrconf,alternate_image=work if work is not image else None,save_evidence=save_ocr_evidence)
                    qr_values[inp.side]=result.payload["values"]
                    return result
                add("QR",qrconf.get("required",False),inp.image_id,qr_step)
        # Parser may only use actually OCR-processed sides.
        fields=extract_fields(lines,aligned,p)
        for side,values in qr_values.items():
            cfg=p["qr"][side]
            if cfg.get("delimiter") and cfg.get("fields"):
                fields.extend(qr_fields(values,side,cfg))
        missing=[f["name"]+":"+f["side"] for f in fields if f["required"] and not f["reliable"]]
        if not lines:
            checks.append(Check("EXTRACTION",Status.SKIPPED,True,reasons=["OCR_NOT_AVAILABLE"]))
        elif not p.get("fields"):
            checks.append(Check("EXTRACTION",Status.INCONCLUSIVE,True,reasons=["FIELD_LAYOUT_NOT_CONFIGURED"]))
        else:
            checks.append(Check("EXTRACTION",Status.INCONCLUSIVE if missing else Status.PASS,True,
                reasons=["REQUIRED_FIELDS_UNREADABLE"] if missing else [],metrics={"missing_fields":missing}))
        comparisons=compare(fields,declared,p)
        if not lines:
            checks.append(Check("CONSISTENCY",Status.SKIPPED,True,reasons=["EXTRACTION_NOT_AVAILABLE"]))
        else:
            required=[x for x in comparisons if x["required"]]
            status=Status.FAIL if any(x["status"]=="FAIL" for x in required) else Status.INCONCLUSIVE if not required or any(x["status"]!="PASS" for x in required) else Status.PASS
            checks.append(Check("CONSISTENCY",status,True,reasons=[] if status==Status.PASS else ["DATA_MISMATCH"] if status==Status.FAIL else ["INSUFFICIENT_COMPARISON_COVERAGE"]))
        # A complete run may early-exit for invalid input; this is not a technical failure.
        technical=[c for c in checks if c.status==Status.ERROR and c.required]
        processing="PARTIAL" if technical else "COMPLETED"
        assessment="INCONCLUSIVE"
        if any(c.status==Status.FAIL and c.check in {"INPUT","FILE","GEOMETRY","QUALITY","ALIGNED_QUALITY","DOCUMENT_TYPE"} for c in checks):assessment="INPUT_UNSUITABLE"
        elif any(c.status==Status.FAIL and c.check=="IMAGE_FORENSICS" and c.required for c in checks):assessment="SUSPICIOUS"
        elif any(c.status==Status.FAIL and c.check=="CONSISTENCY" for c in checks):assessment="INCONSISTENT"
        elif all(c.status in {Status.PASS,Status.NOT_APPLICABLE} for c in checks if c.required):
            if not any(c.get("comparison_mode")=="unaccented_name" for c in comparisons) and not diagnostic and p.get("validated",False) and p.get("quality",{}).get("calibrated",False) and p.get("forensics",{}).get("mode")=="required":assessment="CLEAR"
        runtime={}
        for package in ("numpy","Pillow","opencv-python-headless","paddleocr","paddlepaddle","paddlepaddle-gpu","vietocr","torch","zxing-cpp"):
            try:runtime[package]=version(package)
            except PackageNotFoundError:runtime[package]=None
        report={"runtimeVersions":runtime,"schemaVersion":"1.1","pipelineVersion":"0.7.0","requestId":rid,
            "profileId":p["id"],"profileVersion":p["version"],"configFingerprint":p.get("_fingerprint"),
            "processingStatus":processing,"assessment":assessment,"diagnosticMode":diagnostic,
            "images":[{"imageId":x.image_id,"side":x.side,"sha256":x.sha256} for x in inputs],
            "checks":[asdict(x) for x in checks],"ocrLines":lines,"extractedData":fields,"comparisons":comparisons,
            "qrPayloads":qr_values,"transforms":transformations,
            "coverage":{"template":"reference feature matching only","recapture":"NOT_IMPLEMENTED",
                "forensics":p.get("forensics",{}).get("mode","disabled")},
            "elapsedMs":round((time.perf_counter()-start)*1000,2)}
        report["ocrConfiguration"]={k:p.get("ocr",{}).get(k) for k in ("engine","version","lang","device","detection_model","recognition_model")}
        report["readerSummary"]={side:{
            "ocr_execution":next((c.status for c in checks if c.image_id==next(x.image_id for x in inputs if x.side==side) and c.check=="OCR"),"SKIPPED"),
            "ocr_accuracy":"NOT_EVALUATED",
            "qr_execution":next((c.status for c in checks if c.image_id==next(x.image_id for x in inputs if x.side==side) and c.check=="QR"),"SKIPPED"),
            "qr_payload_count":len(qr_values.get(side,[])),
            "qr_required":bool(p.get("qr",{}).get(side,{}).get("required",False)),
            "authenticity":"NOT_VERIFIED"} for side in seen}
        (folder/"report.json").write_text(dumps(report),encoding="utf-8")
        return report
