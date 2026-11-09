from pathlib import Path

import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from PIL import Image


MODEL_PATH = (
    Path(__file__).resolve().parents[2]
    / "models"
    / "blaze_face_short_range.tflite"
)


def detect_faces(image: Image.Image) -> list[dict]:
    rgb_image = image.convert("RGB")
    pixels = np.array(rgb_image)

    mp_image = mp.Image(
        image_format=mp.ImageFormat.SRGB,
        data=pixels,
    )

    options = vision.FaceDetectorOptions(
        base_options=python.BaseOptions(
            model_asset_path=str(MODEL_PATH),
        ),
        running_mode=vision.RunningMode.IMAGE,
        min_detection_confidence=0.5,
    )

    with vision.FaceDetector.create_from_options(options) as detector:
        result = detector.detect(mp_image)

    faces = []

    for detection in result.detections:
        box = detection.bounding_box

        faces.append({
            "confidence": float(detection.categories[0].score),
            "boundingBox": {
                "x": box.origin_x,
                "y": box.origin_y,
                "width": box.width,
                "height": box.height,
            },
        })

    return faces