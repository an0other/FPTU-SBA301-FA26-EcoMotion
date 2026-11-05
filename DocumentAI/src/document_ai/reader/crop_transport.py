"""Lossless private worker transport. Never resize crops for transport."""
import base64
from PIL import Image

MAX_RAW_BYTES = 24 * 1024 * 1024


def decode_crops(items):
    images = []
    total = 0
    for item in items:
        if isinstance(item, str):
            with Image.open(item) as source:
                images.append(source.convert('RGB'))
            continue
        w, h = item['width'], item['height']
        if type(w) is not int or type(h) is not int or w <= 0 or h <= 0 or w*h > 2_000_000:
            raise ValueError('Invalid raw crop size')
        total += w*h*3
        if total > MAX_RAW_BYTES:
            raise ValueError('Raw crop request exceeds 24 MiB')
        encoded = item['rgb']
        if len(encoded) > ((w*h*3+2)//3)*4:
            raise ValueError('Raw crop encoded length mismatch')
        pixels = base64.b64decode(encoded, validate=True)
        if len(pixels) != w*h*3:
            raise ValueError('Raw crop byte count mismatch')
        images.append(Image.frombytes('RGB', (w,h), pixels))
    return images
