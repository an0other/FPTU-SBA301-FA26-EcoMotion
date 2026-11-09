import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps
from skimage.transform import SimilarityTransform

from identity_verification.scrfd_detection import detect_faces_in_image


REFERENCE_POINTS = np.array(
    [
        [38.2946, 51.6963],
        [73.5318, 51.5014],
        [56.0252, 71.7366],
        [41.5493, 92.3655],
        [70.7299, 92.2041],
    ],
    dtype=np.float64,
)


def align_face_in_image(image: Image.Image) -> Image.Image:
    image = image.convert("RGB")
    faces = detect_faces_in_image(image)

    if len(faces) != 1:
        raise ValueError(
            f"Expected exactly one face, detected {len(faces)}"
        )

    landmarks = np.asarray(
        faces[0]["landmarks"],
        dtype=np.float64,
    )

    if landmarks.shape != (5, 2) or not np.isfinite(landmarks).all():
        raise ValueError("Invalid face landmarks")

    transform = SimilarityTransform.from_estimate(
        landmarks,
        REFERENCE_POINTS,
    )

    if not transform:
        raise ValueError("Could not estimate face alignment")

    inverse_matrix = np.linalg.inv(transform.params)
    coefficients = tuple(inverse_matrix[:2].reshape(-1))

    aligned = image.transform(
        (112, 112),
        Image.Transform.AFFINE,
        coefficients,
        resample=Image.Resampling.BILINEAR,
        fillcolor=(0, 0, 0),
    )

    return aligned

def align_face(image_path: Path) -> Image.Image:
    with Image.open(image_path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    return align_face_in_image(image)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    aligned = align_face(args.image)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    aligned.save(args.output)

    print("Aligned size:", aligned.size)
    print("Saved:", args.output)


if __name__ == "__main__":
    main()