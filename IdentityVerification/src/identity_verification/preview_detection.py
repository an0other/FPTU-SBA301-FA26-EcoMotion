import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

from identity_verification.face_detection import detect_faces


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    with Image.open(args.image) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    faces = detect_faces(image)
    draw = ImageDraw.Draw(image)

    for face in faces:
        box = face["boundingBox"]
        left = box["x"]
        top = box["y"]
        right = left + box["width"]
        bottom = top + box["height"]

        draw.rectangle(
            (left, top, right, bottom),
            outline="lime",
            width=3,
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)

    print(f"Detected faces: {len(faces)}")
    print(f"Saved preview: {args.output}")


if __name__ == "__main__":
    main()