import argparse
import json
from pathlib import Path

from document_ai.core import InputImage, dumps, load_profile
from document_ai.pipeline.runner import Pipeline


def main():
    parser=argparse.ArgumentParser(prog="document-ai")
    sub=parser.add_subparsers(dest="command",required=True)
    doctor=sub.add_parser("doctor")
    doctor.add_argument("--profile",required=True)
    run=sub.add_parser("analyze")
    run.add_argument("--profile",required=True)
    run.add_argument("--front",required=True)
    run.add_argument("--back")
    run.add_argument("--declared",help="Path to JSON file containing string field values")
    run.add_argument("--output",default="runs")
    run.add_argument("--diagnostic",action="store_true")
    run.add_argument("--save-ocr-evidence",action="store_true",help="Save private OCR crops and numbered overlay locally")
    run.add_argument("--corners",help="JSON selections from tools/select_corners.py")
    args=parser.parse_args()
    try:
        p=load_profile(args.profile)
        if args.command=="doctor":
            import importlib.util
            state={"profile":p["id"],"validated":p.get("validated",False),
                "modules":{x:importlib.util.find_spec(x) is not None for x in ("cv2","PIL","paddleocr","paddle","fastapi")},
                "templates":{s:bool(p.get("templates",{}).get(s,{}).get("reference")) for s in p["required_sides"]},
                "forensics":p.get("forensics",{}).get("mode","disabled"),
                "note":"Module availability is not a model inference test"}
            print(dumps(state));return
        inputs=[]
        limit=p.get("limits",{}).get("max_bytes",8*1024*1024)
        for side,name in (("FRONT",args.front),("BACK",args.back)):
            if name:
                with Path(name).open("rb") as f:content=f.read(limit+1)
                inputs.append(InputImage(side,content,side.lower()))
        declared=json.loads(Path(args.declared).read_text(encoding="utf-8")) if args.declared else {}
        if not isinstance(declared,dict) or not all(isinstance(k,str) and isinstance(v,str) for k,v in declared.items()):
            raise ValueError("Declared data must be a string-to-string object")
        corners=json.loads(Path(args.corners).read_text(encoding="utf-8")) if args.corners else None
        if corners is not None and (not isinstance(corners,dict) or any(k not in {"FRONT","BACK"} for k in corners)):
            raise ValueError("Invalid corner selection")
        report=Pipeline(p,args.output).run(inputs,declared,args.diagnostic,args.save_ocr_evidence,corners)
        print(dumps({"requestId":report["requestId"],"assessment":report["assessment"],"report":str(Path(args.output)/report["requestId"]/"report.json")}))
    except (ValueError,OSError) as ex:
        parser.exit(2,f"Configuration/input error ({type(ex).__name__}); check profile, paths and JSON.\n")


if __name__=="__main__":main()
