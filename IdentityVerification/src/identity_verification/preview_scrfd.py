import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

from identity_verification.scrfd_detection import detect_faces


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    with Image.open(args.image) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    faces = detect_faces(args.image)
    draw = ImageDraw.Draw(image)

    radius = max(3, round(min(image.size) * 0.003))
    line_width = max(2, radius // 2)

    colors = ("red", "cyan", "yellow", "lime", "magenta")

    for face in faces:
        draw.rectangle(
            tuple(face["box"]),
            outline="lime",
            width=line_width,
        )

        for point, color in zip(face["landmarks"], colors):
            x, y = point

            draw.ellipse(
                (x - radius, y - radius, x + radius, y + radius),
                fill=color,
            )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)

    print("Image size:", image.size)
    print("Detected faces:", len(faces))
    print("Saved preview:", args.output)


if __name__ == "__main__":
    main()