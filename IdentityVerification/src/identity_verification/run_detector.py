import argparse
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps


INPUT_SIZE = 640


def prepare_pil_image(image: Image.Image) -> np.ndarray:
    image = image.convert("RGB")

    scale = INPUT_SIZE / max(image.size)

    resized_width = max(1, int(image.width * scale))
    resized_height = max(1, int(image.height * scale))

    resized = image.resize(
        (resized_width, resized_height),
        Image.Resampling.BILINEAR,
    )

    canvas = np.zeros(
        (INPUT_SIZE, INPUT_SIZE, 3),
        dtype=np.uint8,
    )
    canvas[:resized_height, :resized_width] = np.asarray(resized)

    pixels = canvas.astype(np.float32)
    pixels = (pixels - 127.5) / 128.0

    tensor = pixels.transpose(2, 0, 1)[np.newaxis, ...]

    return np.ascontiguousarray(tensor)

def prepare_image(path: Path) -> np.ndarray:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    return prepare_pil_image(image)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    args = parser.parse_args()

    model_root = Path(__file__).resolve().parents[2] / "models" / "buffalo_sc"
    matches = list(model_root.rglob("det_500m.onnx"))

    if len(matches) != 1:
        raise RuntimeError("Expected exactly one det_500m.onnx")

    session = ort.InferenceSession(
        str(matches[0]),
        providers=["CPUExecutionProvider"],
    )

    tensor = prepare_image(args.image)

    print("Input shape:", tensor.shape)
    print("Input dtype:", tensor.dtype)

    outputs = session.run(
        None,
        {session.get_inputs()[0].name: tensor},
    )

    for metadata, values in zip(session.get_outputs(), outputs):
        print(f"Output {metadata.name}: {values.shape}")

    for stride, scores in zip((8, 16, 32), outputs[:3]):
        candidate_count = np.count_nonzero(scores >= 0.5)
        print(f"Stride {stride}: {candidate_count} candidates >= 0.5")


if __name__ == "__main__":
    main()