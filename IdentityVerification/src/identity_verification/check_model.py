from pathlib import Path

from mediapipe.tasks import python
from mediapipe.tasks.python import vision


def main():
    service_root = Path(__file__).resolve().parents[2]
    model_path = service_root / "models" / "blaze_face_short_range.tflite"

    options = vision.FaceDetectorOptions(
        base_options=python.BaseOptions(
            model_asset_path=str(model_path),
        ),
        running_mode=vision.RunningMode.IMAGE,
        min_detection_confidence=0.5,
    )

    with vision.FaceDetector.create_from_options(options):
        print("Face detector loaded successfully")


if __name__ == "__main__":
    main()