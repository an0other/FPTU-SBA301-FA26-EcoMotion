"""Fine-tune a nano pose network for four points; no weights bundled."""
import argparse,hashlib,json,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--data',required=True);p.add_argument('--output',required=True)
p.add_argument('--epochs',type=int,default=100);p.add_argument('--batch',type=int,default=2);p.add_argument('--device',default='0');a=p.parse_args()
out=Path(a.output).resolve()
if out.exists():raise ValueError('Output must be new')
from ultralytics import YOLO
model=YOLO('yolo11n-pose.yaml').load('yolo11n-pose.pt')
model.train(data=str(Path(a.data).resolve()),epochs=a.epochs,batch=a.batch,imgsz=640,device=a.device,
            workers=0,project=str(out.parent),name=out.name,exist_ok=False,
            fliplr=0.,flipud=0.,degrees=0.,mosaic=0.,mixup=0.,perspective=0.,seed=42)
best=Path(model.trainer.best)
meta={'task':'card-four-corners/v2','order':'TOPMOST_CLOCKWISE','sha256':hashlib.sha256(best.read_bytes()).hexdigest(),
      'note':'Checkpoint trained; validation and real OCR/QR evaluation still required'}
best.with_suffix('.card.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
print(best)
