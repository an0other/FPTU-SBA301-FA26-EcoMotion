import json
from pathlib import Path

def load_locator(weights, device='cpu', allow_legacy_pose=False):
    meta=json.loads(Path(weights).with_suffix('.card.json').read_text(encoding='utf-8'))
    if meta.get('task')=='card-segmentation/v1':
        from .segmentation import SegmentationLocator
        return SegmentationLocator(weights,device=device)
    if meta.get('task')=='card-four-corners/v2' and allow_legacy_pose:
        from .keypoints import KeypointLocator
        return KeypointLocator(weights,device=device)
    raise ValueError('Legacy pose checkpoint requires explicit --allow-legacy-pose; use new segmentation weights for v0.14')
