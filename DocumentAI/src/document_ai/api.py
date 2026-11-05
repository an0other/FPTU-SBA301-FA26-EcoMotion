import hmac
import json
import os
import threading
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from starlette.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from document_ai.core import InputImage, clean_json, load_profile
from document_ai.pipeline.runner import Pipeline


def create_app(profile_path=None,output_root=None,ocr=None):
    path=profile_path or os.getenv("DOCUMENT_AI_PROFILE","configs/gplx.local.json")
    profile=load_profile(path)
    pipeline=Pipeline(profile,output_root or os.getenv("DOCUMENT_AI_OUTPUT","runs"),ocr)
    lock=threading.Lock()
    app=FastAPI(title="DocumentAI",version="0.13.0")

    def authorize(x_api_key: str | None = Header(default=None)):
        key=os.getenv("DOCUMENT_AI_API_KEY")
        if key and (x_api_key is None or not hmac.compare_digest(key,x_api_key)):
            raise HTTPException(401,"Invalid service API key")

    @app.get("/health")
    def health():return {"status":"ok","version":"0.13.0"}

    @app.get("/v1/capabilities",dependencies=[Depends(authorize)])
    def capabilities():
        return {"documentType":profile["document_type"],"profileId":profile["id"],
            "requiredSides":profile["required_sides"],"validated":profile.get("validated",False),
            "recaptureDetector":False,"templateMethod":"ORB_REFERENCE_BASELINE",
            "forensicsMode":profile.get("forensics",{}).get("mode","disabled"),
            "note":"Capability configuration is not proof of successful model loading"}

    @app.post("/v1/analyses",dependencies=[Depends(authorize)])
    async def analyze(front: UploadFile=File(...),back: UploadFile | None=File(None),
                      document_type: str=Form(...),declared_data: str=Form("{}")):
        if document_type!=profile["document_type"]:
            raise HTTPException(422,"Requested document type does not match configured profile")
        try:
            declared=json.loads(declared_data)
            if not isinstance(declared,dict) or not all(isinstance(k,str) and isinstance(v,str) for k,v in declared.items()):raise ValueError()
        except (ValueError,TypeError):raise HTTPException(422,"declared_data must be a string-to-string JSON object")
        if not lock.acquire(blocking=False):raise HTTPException(429,"Worker busy; retry later")
        try:
            limit=profile.get("limits",{}).get("max_bytes",8*1024*1024)
            inputs=[]
            for side,f in (("FRONT",front),("BACK",back)):
                if f:
                    content=await f.read(limit+1)
                    if len(content)>limit:raise HTTPException(413,"Image exceeds file size limit")
                    inputs.append(InputImage(side,content,side.lower()))
            report=await run_in_threadpool(pipeline.run,inputs,declared,False)
            return JSONResponse(clean_json(report))
        finally:
            lock.release()
            await front.close()
            if back:await back.close()
    return app
