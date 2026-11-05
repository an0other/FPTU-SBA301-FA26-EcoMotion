import hashlib,importlib.util,json
from pathlib import Path
import pytest
from PIL import Image
from document_ai.reader.keypoints import KeypointLocator,validate_points
ROOT=Path(__file__).resolve().parents[1]


def builder():
    spec=importlib.util.spec_from_file_location('corner_builder',ROOT/'tools/build_corner_dataset.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module.build


def test_no_generic_checkpoint_without_bound_metadata(tmp_path):
    weights=tmp_path/'best.pt';weights.write_bytes(b'test')
    weights.with_suffix('.card.json').write_text(json.dumps({'task':'card-four-corners/v2','order':'TOPMOST_CLOCKWISE','sha256':'bad'}))
    with pytest.raises(ValueError,match='hash mismatch'):KeypointLocator(weights)
    with pytest.raises(ValueError):validate_points([[0,0],[20,0],[20,20],[0,20]],10,10)


def test_group_leakage_rejected_before_output(tmp_path):
    image=tmp_path/'image.png';Image.new('RGB',(800,500)).save(image)
    sha=hashlib.sha256(image.read_bytes()).hexdigest()
    corners=tmp_path/'corners.json';corners.write_text(json.dumps({'FRONT':{'sha256':sha,'corners':[[100,100],[700,100],[700,400],[100,400]]}}))
    manifest=tmp_path/'manifest.json'
    items=[{'image':'image.png','corners':'corners.json','side':'FRONT','group':'same-card','split':'train'}, {'image':'image.png','corners':'corners.json','side':'FRONT','group':'same-card','split':'val'}]
    manifest.write_text(json.dumps({'images':items}))
    with pytest.raises(ValueError,match='Same group'):builder()(manifest,tmp_path/'out')
    assert not (tmp_path/'out').exists()


def test_export_oriented_labels_and_negatives(tmp_path):
    Image.new('RGB',(800,500),'white').save(tmp_path/'card.png')
    Image.new('RGB',(800,500),'black').save(tmp_path/'negative.png')
    sha=hashlib.sha256((tmp_path/'card.png').read_bytes()).hexdigest()
    (tmp_path/'corners.json').write_text(json.dumps({'FRONT':{'sha256':sha,'corners':[[100,100],[700,100],[700,400],[100,400]]}}))
    items=[{'image':'card.png','corners':'corners.json','side':'FRONT','group':'card-a','split':'train'}, {'image':'negative.png','negative':True,'group':'background-b','split':'val'}]
    Image.new('RGB',(800,500),'gray').save(tmp_path/'validation.png')
    val_sha=hashlib.sha256((tmp_path/'validation.png').read_bytes()).hexdigest()
    (tmp_path/'validation-corners.json').write_text(json.dumps({'FRONT':{'sha256':val_sha,'corners':[[100,100],[700,100],[700,400],[100,400]]}}))
    items.append({'image':'validation.png','corners':'validation-corners.json','side':'FRONT','group':'card-c','split':'val'})
    manifest=tmp_path/'m.json';manifest.write_text(json.dumps({'images':items}));builder()(manifest,tmp_path/'out',quarter_turns=True)
    label=(tmp_path/'out/labels/train/000000.txt').read_text().split()
    assert len(label)==17
    rotated=Image.open(tmp_path/'out/images/train/000000-r90.png')
    assert rotated.size==(500,800)
    rotated_label=[float(x) for x in (tmp_path/'out/labels/train/000000-r90.txt').read_text().split()]
    assert len(rotated_label)==17 and all(0<=rotated_label[i]<=1 for i in [5,6,8,9,11,12,14,15])
    assert (tmp_path/'out/labels/val/000001.txt').read_text()==''
    assert (tmp_path/'out/dataset.yaml').exists()
