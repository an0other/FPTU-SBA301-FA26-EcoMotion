import argparse
from pathlib import Path
import csv

import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from PIL import Image, ImageOps


def estimate_yaw(matrix):
    rotation = np.asarray(matrix, dtype=np.float64)[:3, :3]

    u, _, vt = np.linalg.svd(rotation)
    correction = np.eye(3)
    correction[2, 2] = np.linalg.det(u @ vt)
    rotation = u @ correction @ vt

    angle = np.arctan2(
        -rotation[2, 0],
        np.hypot(rotation[0, 0], rotation[1, 0]),
    )

    return float(np.degrees(angle))

def create_landmarker():
    model_path = (
        Path(__file__).resolve().parents[2]
        / "models"
        / "face_landmarker.task"
    )

    options = vision.FaceLandmarkerOptions(
        base_options=python.BaseOptions(
            model_asset_path=str(model_path),
        ),
        running_mode=vision.RunningMode.IMAGE,
        num_faces=2,
        output_facial_transformation_matrixes=True,
    )

    return vision.FaceLandmarker.create_from_options(options)


def inspect_pose_in_image(landmarker, image: Image.Image):
    pixels = np.ascontiguousarray(
        np.asarray(image.convert("RGB"))
    )

    result = landmarker.detect(
        mp.Image(
            image_format=mp.ImageFormat.SRGB,
            data=pixels,
        )
    )

    count = len(result.face_landmarks)

    if count != 1:
        return {"faces": count, "yaw": None}

    matrices = result.facial_transformation_matrixes

    if len(matrices) != 1:
        return {"faces": count, "yaw": None}

    yaw = estimate_yaw(matrices[0])

    return {
        "faces": count,
        "yaw": yaw if np.isfinite(yaw) else None,
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("frames", type=Path)
    args = parser.parse_args()
    rows = []

    paths = sorted(args.frames.glob("frame_*.png"))

    if not paths:
        raise ValueError("No sampled frames found")

    

    with create_landmarker() as landmarker:
        for path in paths:
            with Image.open(path) as source:
                image = ImageOps.exif_transpose(source).convert("RGB")

            pixels = np.ascontiguousarray(np.asarray(image))

            result = landmarker.detect(
                mp.Image(
                    image_format=mp.ImageFormat.SRGB,
                    data=pixels,
                )
            )

            count = len(result.face_landmarks)

            if count != 1:
                print(f"{path.name}: faces={count}, skipped")
                rows.append({
                    "frame": path.name,
                    "faces": count,
                    "yaw": "",
                })
                continue

            matrices = result.facial_transformation_matrixes

            if len(matrices) != 1:
                print(f"{path.name}: pose unavailable")
                rows.append({
                    "frame": path.name,
                    "faces": count,
                    "yaw": "",
                })
                continue

            yaw = estimate_yaw(matrices[0])
            rows.append({
                "frame": path.name,
                "faces": count,
                "yaw": yaw,
            })

            print(f"{path.name}: faces=1, yaw={yaw:+.1f} degrees")
    report_path = args.frames / "head_pose.csv"

    with report_path.open("w", newline="", encoding="utf-8") as report:
        writer = csv.DictWriter(
            report,
            fieldnames=["frame", "faces", "yaw"],
        )
        writer.writeheader()
        writer.writerows(rows)

    print("Saved report:", report_path)

if __name__ == "__main__":
    main()