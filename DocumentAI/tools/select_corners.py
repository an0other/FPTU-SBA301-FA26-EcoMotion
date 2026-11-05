import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from document_ai.reader.corner_order import order_corners
"""Windows Tk picker; click four corners in any order on the EXIF-oriented original."""
import argparse
import hashlib
import json
from pathlib import Path
import tkinter as tk
from PIL import Image, ImageOps, ImageTk

parser=argparse.ArgumentParser()
parser.add_argument('--image',required=True)
parser.add_argument('--side',required=True,choices=['FRONT','BACK'])
parser.add_argument('--output',default='configs/corners.local.json')
a=parser.parse_args()
raw=Path(a.image).read_bytes()
with Image.open(a.image) as source:image=ImageOps.exif_transpose(source).convert('RGB')
root=tk.Tk();root.title('Select four physical corners in any order | Undo / Save')
scale=min(1.,1000/image.width,700/image.height)
preview=image.resize((int(image.width*scale),int(image.height*scale)))
photo=ImageTk.PhotoImage(preview)
canvas=tk.Canvas(root,width=preview.width,height=preview.height);canvas.pack()
points=[]
label=tk.Label(root,text='Click four card corners (any order); content can face any direction');label.pack()
def render():
 canvas.delete('all');canvas.create_image(0,0,image=photo,anchor='nw')
 for i,(x,y) in enumerate(points):
  canvas.create_oval(x-5,y-5,x+5,y+5,outline='red',width=3)
  canvas.create_text(x+12,y+12,text=str(i+1),fill='red')
 if len(points)>1:canvas.create_line(*[v for q in points for v in q],fill='red',width=2)
def click(event):
 if len(points)<4:
  points.append((max(0,min(preview.width-1,event.x)),max(0,min(preview.height-1,event.y))));render()
def undo():
 if points:points.pop();render()
def save():
 if len(points)!=4:label.config(text='Select exactly four corners first');return
 path=Path(a.output)
 data=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
 try:ordered=order_corners([[x/scale,y/scale] for x,y in points]).tolist()
 except ValueError:label.config(text='Invalid quadrilateral; undo and select actual corners');return
 data[a.side]={'sha256':hashlib.sha256(raw).hexdigest(),'cornerConvention':'TOPMOST_CLOCKWISE/v2','corners':ordered}
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(data,indent=2),encoding='utf-8')
 label.config(text='Saved. Close window to finish.')
canvas.bind('<Button-1>',click)
tk.Button(root,text='Undo',command=undo).pack(side='left')
tk.Button(root,text='Save',command=save).pack(side='right')
render();root.mainloop()
