"""Explicit group splits; EXIF-oriented exports align with existing corner picker."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
import argparse,hashlib,json
from PIL import Image,ImageOps
import numpy as np
import yaml
from document_ai.geometry import rectify
from document_ai.reader.corner_order import order_corners,quarter_turn_points


def build(manifest_path,output,quarter_turns=False):
    manifest_path=Path(manifest_path).resolve();base=manifest_path.parent
    items=json.loads(manifest_path.read_text(encoding='utf-8-sig'))['images']
    if not items:raise ValueError('No labeled images')
    groups={};hashes=set();validated=[]
    for item in items:
        split=item['split'];group=item['group']
        if split not in {'train','val','test'} or not isinstance(group,str) or not group:raise ValueError('Explicit split and group required')
        if group in groups and groups[group]!=split:raise ValueError('Same group occurs in different splits')
        groups[group]=split
        raw=(base/item['image']).read_bytes();sha=hashlib.sha256(raw).hexdigest()
        if sha in hashes:raise ValueError('Duplicate source image')
        hashes.add(sha)
        with Image.open(base/item['image']) as source:image=ImageOps.exif_transpose(source).convert('RGB')
        if item.get('negative'):
            validated.append((item,image,None,sha));continue
        selection=json.loads((base/item['corners']).read_text(encoding='utf-8-sig'))[item['side']]
        selection={**selection,'corners':order_corners(selection['corners']).tolist()}
        result=rectify(image,selection,sha)
        if result.status!='PASS':raise ValueError('Invalid/stale corner annotation: '+','.join(result.reasons))
        validated.append((item,image,np.asarray(selection['corners'],dtype=float),sha))
    if not {'train','val'}.issubset({i['split'] for i in items}):raise ValueError('Train and validation splits required')
    if not all(any(i['split']==split and not i.get('negative') for i in items) for split in ('train','val')):raise ValueError('Train and val each need labeled card images')
    out=Path(output).resolve();out.mkdir(parents=True,exist_ok=False);index=[]
    for number,(item,image,pts,sha) in enumerate(validated):
        split=item['split']
        angles=(0,90,180,270) if quarter_turns and split=='train' else (0,)
        for angle in angles:
            stem=f'{number:06d}'+(f'-r{angle}' if angle else '')
            work=image if angle==0 else image.rotate(angle,expand=True)
            w,h=work.size
            for name in ('images','labels'):(out/name/split).mkdir(parents=True,exist_ok=True)
            work.save(out/'images'/split/(stem+'.png'))
            if pts is None:label=''
            else:
                rotated=order_corners(quarter_turn_points(pts,*image.size,angle))
                x1,y1=rotated.min(axis=0);x2,y2=rotated.max(axis=0)
                values=[0,(x1+x2)/(2*w),(y1+y2)/(2*h),(x2-x1)/w,(y2-y1)/h]
                for x,y in rotated:values += [x/w,y/h,2]
                label=' '.join(str(v) for v in values)+'\n'
            (out/'labels'/split/(stem+'.txt')).write_text(label)
            index.append({'export':stem,'split':split,'group':item['group'],'sha256':sha,'negative':pts is None,'rotationCounterClockwise':angle,'cornerConvention':'TOPMOST_CLOCKWISE/v2'})
    cfg={'path':str(out),'train':'images/train','val':'images/val','names':{0:'document_card'},'kpt_shape':[4,3],'flip_idx':[1,0,3,2]}
    if any(i['split']=='test' for i in items):cfg['test']='images/test'
    (out/'dataset.yaml').write_text(yaml.safe_dump(cfg,sort_keys=False))
    (out/'index.json').write_text(json.dumps(index,indent=2))
    print(out/'dataset.yaml')


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--manifest',required=True);p.add_argument('--output',required=True);p.add_argument('--quarter-turns',action='store_true');a=p.parse_args();build(a.manifest,a.output,a.quarter_turns)
