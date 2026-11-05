"""Compare saved Paddle lines with VietOCR, without changing reader output or QR."""
import sys,argparse,json,hashlib,time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from PIL import Image,ImageOps
from document_ai.adapters.ocr import crop_line,VietOcrRecognizer
from document_ai.name_comparison import unaccent_name

def main():
 p=argparse.ArgumentParser();p.add_argument('--image',required=True);p.add_argument('--lines',required=True);p.add_argument('--output',required=True);p.add_argument('--device',default='cpu');p.add_argument('--model',default='vgg_seq2seq');p.add_argument('--export-only',action='store_true');a=p.parse_args()
 image=ImageOps.exif_transpose(Image.open(a.image)).convert('RGB');lines=json.loads(Path(a.lines).read_text(encoding='utf-8-sig'));out=Path(a.output);out.mkdir(parents=True,exist_ok=False)
 recognizer=None if a.export_only else VietOcrRecognizer(a.device,a.model)
 rows=[]
 for i,l in enumerate(lines):
  row={'index':i,'paddleText':l['text'],'paddleConfidence':l.get('confidence'),'polygon':l.get('polygon')}
  try:
   crop=crop_line(image,l['polygon']);crop.save(out/f'line-{i:03}.png')
   if recognizer:
    start=time.perf_counter();text,confidence=recognizer.recognize(crop)
    row.update(vietocrText=text,vietocrConfidence=confidence,elapsedMs=(time.perf_counter()-start)*1000,exactMatch=text==l['text'],unaccentedMatch=unaccent_name(text)==unaccent_name(l['text']))
  except Exception as e:row['error']=type(e).__name__
  rows.append(row)
 report={'imageSha256':hashlib.sha256(Path(a.image).read_bytes()).hexdigest(),'model':a.model,'note':'Independent recognizer comparison; no automatic correction, no QR labels, confidences not comparable. Check each saved crop matches its intended line.','lines':rows}
 (out/'comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(out/'comparison.json')
if __name__=='__main__':main()
