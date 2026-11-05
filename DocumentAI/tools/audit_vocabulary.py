"""Inspect installed model metadata without editing its vocabulary or weights."""
import argparse
import json
from pathlib import Path
import unicodedata
import yaml

p=argparse.ArgumentParser()
p.add_argument('--model-dir',required=True)
p.add_argument('--text-file',required=True)
p.add_argument('--output',required=True)
a=p.parse_args()
text=unicodedata.normalize('NFC',Path(a.text_file).read_text(encoding='utf-8-sig'))
root=Path(a.model_dir)
if not root.is_dir():p.error('Model directory not found')
found=[]
def walk(value,source,path=''):
 if isinstance(value,dict):
  for key,item in value.items():
   if key in {'character_dict','character_list','characters'} and isinstance(item,list) and all(isinstance(x,str) for x in item):
    found.append((source,path+'.'+key,item))
   walk(item,source,path+'.'+key)
 elif isinstance(value,list):
  for i,item in enumerate(value):
   if isinstance(item,(dict,list)):walk(item,source,path+f'[{i}]')
for file in list(root.glob('*.yml'))+list(root.glob('*.yaml'))+list(root.glob('*.json')):
 if file.stat().st_size>10_000_000:continue
 value=json.loads(file.read_text(encoding='utf-8')) if file.suffix=='.json' else yaml.safe_load(file.read_text(encoding='utf-8'))
 walk(value,file.name)
result={'model_dir':root.name,'status':'VOCABULARY_NOT_FOUND','note':'Metadata coverage only; no proof of recognition accuracy. Do not edit dictionary without matching trained weights.'}
if len(found)==1:
 source,key,entries=found[0];alphabet=set(''.join(entries));needed={x for x in text if not x.isspace()}
 missing=sorted(needed-alphabet)
 result.update(status='MISSING_CHARACTERS' if missing else 'CHARACTERS_PRESENT',metadata_file=source,key=key,missing_characters=missing,vocabulary_entries=len(entries))
elif len(found)>1:result.update(status='AMBIGUOUS_VOCABULARIES',count=len(found))
output=Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
with output.open('x',encoding='utf-8') as f:json.dump(result,f,ensure_ascii=False,indent=2)
print(str(output))
