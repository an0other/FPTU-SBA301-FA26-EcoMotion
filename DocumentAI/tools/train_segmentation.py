"""Train mask model; this is an alternative to v0.13 pose, not a proven accuracy fix."""
import argparse, hashlib, json
from pathlib import Path

def main():
    p=argparse.ArgumentParser();p.add_argument('--data',required=True);p.add_argument('--output',required=True)
    p.add_argument('--epochs',type=int,default=60);p.add_argument('--batch',type=int,default=2);p.add_argument('--device',default='0')
    a=p.parse_args();out=Path(a.output).resolve()
    if out.exists():raise ValueError('Use new output folder')
    from ultralytics import YOLO
    model=YOLO('yolo11n-seg.pt')
    model.train(data=str(Path(a.data).resolve()),epochs=a.epochs,batch=a.batch,imgsz=640,device=a.device,
                workers=0,project=str(out.parent),name=out.name,exist_ok=False,seed=42,
                fliplr=0.,flipud=0.,degrees=0.,mosaic=0.,mixup=0.,perspective=0.,
                translate=.05,scale=.15,hsv_h=.01,hsv_s=.3,hsv_v=.2)
    best=Path(model.trainer.best)
    meta={'task':'card-segmentation/v1','sha256':hashlib.sha256(best.read_bytes()).hexdigest(),
          'note':'Experimental; evaluate corner error and crop before deployment'}
    best.with_suffix('.card.json').write_text(json.dumps(meta,indent=2),encoding='utf-8');print(best)

if __name__=='__main__':main()
