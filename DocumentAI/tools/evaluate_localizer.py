"""Evaluate actual corner distance and polygon overlap; no authenticity score."""
import sys,argparse,json,itertools,hashlib,time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import numpy as np
import cv2
from PIL import Image,ImageDraw
from document_ai.reader.localizers import load_locator
from document_ai.reader.geometry import locate

def compare_corners(predicted,expected,w,h):
    p=np.asarray(predicted,float);t=np.asarray(expected,float)
    e=min((np.linalg.norm(p-t[list(q)],axis=1) for q in itertools.permutations(range(4))),key=lambda x:x.mean())
    scale=min(1.,1000/max(w,h));shape=(max(2,round(h*scale)),max(2,round(w*scale)));masks=[]
    for points in (p,t):
        c=points.mean(axis=0);points=points[np.argsort(np.arctan2(points[:,1]-c[1],points[:,0]-c[0]))]
        m=np.zeros(shape,np.uint8);cv2.fillConvexPoly(m,np.rint(points*scale).astype(np.int32),1);masks.append(m)
    iou=np.count_nonzero(masks[0]&masks[1])/max(1,np.count_nonzero(masks[0]|masks[1]))
    return dict(meanCornerErrorPx=float(e.mean()),maxCornerErrorPx=float(e.max()),meanCornerErrorOverDiagonal=float(e.mean()/np.hypot(w,h)),polygonIoU=iou)

def main():
    p=argparse.ArgumentParser();p.add_argument('--dataset',required=True);p.add_argument('--weights');p.add_argument('--split',choices=['train','val','test'],default='val');p.add_argument('--device',default='cpu');p.add_argument('--allow-legacy-pose',action='store_true');p.add_argument('--output',required=True);a=p.parse_args()
    root=Path(a.dataset);out=Path(a.output);out.mkdir(parents=True,exist_ok=False)
    locator=load_locator(a.weights,a.device,a.allow_legacy_pose) if a.weights else locate
    cases=[]
    for item in json.loads((root/'index.json').read_text(encoding='utf-8-sig')):
        if item['split']!=a.split or item.get('negative'):continue
        stem=item['export'];image=Image.open(root/'images'/a.split/(stem+'.png')).convert('RGB');w,h=image.size
        v=np.asarray(list(map(float,(root/'labels'/a.split/(stem+'.txt')).read_text().split())))
        if len(v)==17:t=v[5:].reshape(4,3)[:,:2]*[w,h]
        elif len(v)==9:t=v[1:].reshape(4,2)*[w,h]
        else:raise ValueError('Expected four-point or quad label')
        begin=time.perf_counter();g=locator(image,hashlib.sha256(image.tobytes()).hexdigest());row=dict(image=stem,status=g['status'],reason=g.get('reason'),durationMs=(time.perf_counter()-begin)*1000)
        canvas=image.copy();draw=ImageDraw.Draw(canvas);xy=[tuple(x) for x in t];draw.line(xy+[xy[0]],fill='lime',width=5)
        if g['status']=='LOCATED':
            row.update(compare_corners(g['corners'],t,w,h));xy=[tuple(x) for x in g['corners']];draw.line(xy+[xy[0]],fill='red',width=4);g['image'].save(out/(stem+'-crop.png'))
        canvas.save(out/(stem+'-overlay.jpg'));cases.append(row)
    found=[x for x in cases if x['status']=='LOCATED']
    report=dict(count=len(cases),located=len(found),notLocated=len(cases)-len(found),meanCornerErrorPxOnLocated=float(np.mean([x['meanCornerErrorPx'] for x in found])) if found else None,meanPolygonIoUOnLocated=float(np.mean([x['polygonIoU'] for x in found])) if found else None,note='Green label; red prediction. Failures counted separately. Same-card pilot, not authenticity or independent-owner validation.',cases=cases)
    (out/'evaluation.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in report.items() if k!='cases'},indent=2))
if __name__=='__main__':main()
