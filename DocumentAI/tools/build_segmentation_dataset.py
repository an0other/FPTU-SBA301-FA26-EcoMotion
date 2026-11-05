"""Reuse existing YOLO-pose exports. No source relabeling and no split changes."""
import argparse
import json
import shutil
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'src'))
import cv2
import numpy as np
import yaml
from PIL import Image


def convert(source, output):
    source, output = Path(source).resolve(), Path(output).resolve()
    index = json.loads((source/'index.json').read_text(encoding='utf-8-sig'))
    if not index:
        raise ValueError('Empty dataset')
    if not {'train', 'val'}.issubset({item['split'] for item in index}):
        raise ValueError('Train/val required; existing split is preserved')
    checked=[]; seen=set(); hashes={}
    for item in index:
        split, stem = item['split'], item['export']
        if split not in ('train','val','test') or Path(stem).name != stem or (split,stem) in seen:
            raise ValueError('Invalid/duplicate index entry')
        seen.add((split,stem))
        previous=hashes.setdefault(item['sha256'],split)
        if previous != split: raise ValueError('Same source image crosses splits')
        image = source/'images'/split/(stem+'.png')
        with Image.open(image) as im: im.verify()
        raw = (source/'labels'/split/(stem+'.txt')).read_text().strip()
        if not raw:
            if not item.get('negative'): raise ValueError('Missing positive label: '+stem)
            text=''
        else:
            a=np.asarray([float(v) for v in raw.split()])
            if a.shape!=(17,) or a[0]!=0 or not np.isfinite(a).all(): raise ValueError('Expected one 4-keypoint label')
            points=a[5:].reshape(4,3)
            if (points[:,:2]<0).any() or (points[:,:2]>1).any() or not(points[:,2]==2).all(): raise ValueError('Invalid visible corners')
            center=points[:,:2].mean(axis=0)
            p=points[:,:2][np.argsort(np.arctan2(points[:,1]-center[1],points[:,0]-center[0]))]
            if not cv2.isContourConvex(p.astype(np.float32)):raise ValueError('Non-convex label')
            # A filled polygon is unchanged by cyclic start-index shifts.
            text='0 '+' '.join(f'{v:.10f}' for v in p.flatten())+'\n'
        checked.append((image,split,stem,text))
    output.mkdir(parents=True,exist_ok=False)
    for image,split,stem,text in checked:
        (output/'images'/split).mkdir(parents=True,exist_ok=True)
        (output/'labels'/split).mkdir(parents=True,exist_ok=True)
        shutil.copy2(image,output/'images'/split/image.name)
        (output/'labels'/split/(stem+'.txt')).write_text(text,encoding='utf-8')
    cfg={'path':str(output),'train':'images/train','val':'images/val','names':{0:'document_card'}}
    if any(x['split']=='test' for x in index):cfg['test']='images/test'
    (output/'dataset.yaml').write_text(yaml.safe_dump(cfg,sort_keys=False),encoding='utf-8')
    (output/'index.json').write_text(json.dumps(index,indent=2),encoding='utf-8')
    (output/'conversion.json').write_text(json.dumps({'task':'card-segmentation/v1','imageCount':len(index),'source':str(source),'splitChanged':False,'scope':'Same-card pilot if original split shares card owners'},indent=2),encoding='utf-8')
    print(output/'dataset.yaml')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--output',required=True)
    a=p.parse_args();convert(a.source,a.output)
