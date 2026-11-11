import argparse
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image
from functools import lru_cache

@lru_cache(maxsize=1)
def create_session():
    model_root = Path(__file__).resolve().parents[2] / "models" / "buffalo_sc"
    matches = list(model_root.rglob("w600k_mbf.onnx"))

    if len(matches) != 1:
        raise RuntimeError("Expected exactly one w600k_mbf.onnx")

    return ort.InferenceSession(
        str(matches[0]),
        providers=["CPUExecutionProvider"],
    )


def extract_embedding_from_image(session, image: Image.Image):
    image = image.convert("RGB")

    if image.size != (112, 112):
        raise ValueError(
            f"Expected aligned image 112x112, received {image.size}"
        )

    pixels = np.asarray(image, dtype=np.float32)
    pixels = (pixels - 127.5) / 127.5

    tensor = pixels.transpose(2, 0, 1)[np.newaxis, ...]
    tensor = np.ascontiguousarray(tensor, dtype=np.float32)

    output = session.run(
        None,
        {session.get_inputs()[0].name: tensor},
    )[0]

    embedding = np.asarray(output, dtype=np.float32).reshape(-1)

    if embedding.size != 512 or not np.isfinite(embedding).all():
        raise ValueError("Model returned an invalid embedding")

    length = float(np.linalg.norm(embedding))

    if length < 1e-8:
        raise ValueError("Model returned a zero-length embedding")

    return embedding / length

def extract_embedding(session, image_path: Path):
    with Image.open(image_path) as source:
        image = source.convert("RGB")

    return extract_embedding_from_image(session, image)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("first_image", type=Path)
    parser.add_argument("second_image", type=Path)
    args = parser.parse_args()

    session = create_session()

    first = extract_embedding(session, args.first_image)
    second = extract_embedding(session, args.second_image)

    similarity = float(np.clip(np.dot(first, second), -1.0, 1.0))

    print("Embedding dimensions:", first.size)
    print(f"Cosine similarity: {similarity:.6f}")
    print("Decision: not evaluated; threshold not calibrated")


if __name__ == "__main__":
    main()