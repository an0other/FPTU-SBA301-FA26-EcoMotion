"""Enroll cropped references, never label the profile as validated."""
import argparse
import json
import shutil
from pathlib import Path
from PIL import Image

p=argparse.ArgumentParser()
p.add_argument("--type",choices=["gplx","cccd"],required=True)
p.add_argument("--front",required=True,help="Cropped reference containing only the card, upright")
p.add_argument("--back",required=True)
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
profile=json.loads((root/"configs"/(a.type+".example.json")).read_text(encoding="utf-8"))
for side,src in [("FRONT",a.front),("BACK",a.back)]:
    with Image.open(src) as im:
        if min(im.size)<400 or im.width*im.height>20_000_000:raise ValueError("Reference resolution outside limits")
        im.verify()
    dest=root/"refs"/a.type/side.lower()/Path(src).name
    dest.parent.mkdir(parents=True,exist_ok=True)
    if Path(src).resolve()!=dest.resolve():shutil.copyfile(src,dest)
    profile["templates"][side]["reference"]="../"+dest.relative_to(root).as_posix()
path=root/"configs"/(a.type+".local.json")
if path.exists():raise SystemExit("Local profile exists; refusing to overwrite manual fields")
path.write_text(json.dumps(profile,ensure_ascii=False,indent=2),encoding="utf-8")
print(path)
print("References enrolled; fields/QR/comparisons still need mapping. Profile remains unvalidated.")
