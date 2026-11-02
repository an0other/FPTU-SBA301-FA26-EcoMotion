import argparse
from pathlib import Path
from PIL import Image,ImageDraw
p=argparse.ArgumentParser()
p.add_argument("image")
p.add_argument("--roi",nargs=4,type=float,required=True)
p.add_argument("--output",default="runs/roi-preview.png")
a=p.parse_args();x1,y1,x2,y2=a.roi
if not 0<=x1<x2<=1 or not 0<=y1<y2<=1:raise SystemExit("Expected normalized x1 y1 x2 y2")
with Image.open(a.image) as im:
    im=im.convert("RGB");w,h=im.size
    ImageDraw.Draw(im).rectangle([x1*w,y1*h,x2*w,y2*h],outline="red",width=3)
    dest=Path(a.output);dest.parent.mkdir(parents=True,exist_ok=True);im.save(dest)
print(dest)
