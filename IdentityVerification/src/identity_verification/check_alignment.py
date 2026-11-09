from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from identity_verification.face_alignment import (
    align_face,
    align_face_in_image,
)


def main():
    path = Path("inputs/cccd-front.jpeg")

    first = align_face(path)

    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    second = align_face_in_image(image)

    print("Aligned size:", second.size)
    print(
        "Path result equals memory result:",
        np.array_equal(np.asarray(first), np.asarray(second)),
    )


if __name__ == "__main__":
    main()