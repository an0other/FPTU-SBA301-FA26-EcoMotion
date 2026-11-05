"""Run in isolated VietOCR environment; compare exact same saved line crops."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"src"))
import argparse
import json
from pathlib import Path
from PIL import Image
from document_ai.adapters.ocr import VietOcrRecognizer, canonical
from document_ai.core import dumps


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--report",required=True)
    parser.add_argument("--device",default="cpu")
    parser.add_argument("--weights")
    parser.add_argument("--model",default="vgg_transformer")
    args=parser.parse_args()
    path=Path(args.report); report=json.loads(path.read_text(encoding="utf-8"))
    recognizer=VietOcrRecognizer(args.device,args.model,args.weights)
    rows=[]
    for entry in report["images"]:
        for index,line in enumerate(report.get("ocrLines",{}).get(entry["side"],[])):
            crop=path.parent/f"{entry['imageId']}-ocr-line-{index:03d}.png"
            if not crop.is_file():
                raise ValueError("Missing crops: rerun analyze with --save-ocr-evidence")
            with Image.open(crop) as image: text,score=recognizer.recognize(image)
            rows.append({"side":entry["side"],"line":index,"crop":crop.name,
                "paddle":line["text"],"vietocr":text,"vietocr_confidence":score,
                "agreement":canonical(text)==canonical(line["text"])})
    output=path.parent/"recognizer-comparison.json"
    output.write_text(dumps({"source_request_id":report["requestId"],"rows":rows,
        "note":"Agreement is not ground truth. Original report is unchanged."}),encoding="utf-8")
    print(str(output))


if __name__=="__main__":main()
