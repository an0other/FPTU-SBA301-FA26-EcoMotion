import argparse
from pathlib import Path

from functools import lru_cache

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps

from identity_verification.run_detector import INPUT_SIZE, prepare_pil_image

def select_unique_faces(boxes, scores, threshold=0.4):
    order = np.argsort(scores)[::-1]
    selected = []

    while order.size:
        current = int(order[0])
        selected.append(current)
        remaining = order[1:]

        if remaining.size == 0:
            break

        top_left = np.maximum(boxes[current, :2], boxes[remaining, :2])
        bottom_right = np.minimum(boxes[current, 2:], boxes[remaining, 2:])

        intersection_size = np.maximum(0, bottom_right - top_left)
        intersection = intersection_size[:, 0] * intersection_size[:, 1]

        current_size = np.maximum(0, boxes[current, 2:] - boxes[current, :2])
        remaining_sizes = np.maximum(
            0,
            boxes[remaining, 2:] - boxes[remaining, :2],
        )

        current_area = current_size[0] * current_size[1]
        remaining_areas = remaining_sizes[:, 0] * remaining_sizes[:, 1]

        union = current_area + remaining_areas - intersection
        overlap = intersection / np.maximum(union, 1e-6)

        order = remaining[overlap <= threshold]

    return selected

@lru_cache(maxsize=1)
def get_detector_session():
    model_root = Path(__file__).resolve().parents[2] / "models" / "buffalo_sc"
    matches = list(model_root.rglob("det_500m.onnx"))

    if len(matches) != 1:
        raise RuntimeError("Expected exactly one det_500m.onnx")

    return ort.InferenceSession(
        str(matches[0]),
        providers=["CPUExecutionProvider"],
    )

def detect_faces_in_image(image: Image.Image):
    session = get_detector_session()

    image = image.convert("RGB")
    original_width, original_height = image.size

    scale = INPUT_SIZE / max(original_width, original_height)
    resized_width = max(1, int(original_width * scale))
    resized_height = max(1, int(original_height * scale))

    outputs = session.run(
        None,
        {session.get_inputs()[0].name: prepare_pil_image(image)},
    )

    all_boxes = []
    all_scores = []
    all_points = []

    for level, stride in enumerate((8, 16, 32)):
        scores = outputs[level].reshape(-1)
        distances = outputs[level + 3].reshape(-1, 4) * stride
        offsets = outputs[level + 6].reshape(-1, 5, 2) * stride

        grid_size = INPUT_SIZE // stride
        grid_y, grid_x = np.mgrid[:grid_size, :grid_size]

        centers = np.stack((grid_x, grid_y), axis=-1).reshape(-1, 2)
        centers = np.repeat(centers, 2, axis=0) * stride

        chosen = np.flatnonzero(scores >= 0.5)

        if chosen.size == 0:
            continue

        centers = centers[chosen]
        distances = distances[chosen]

        boxes = np.concatenate(
            (
                centers - distances[:, :2],
                centers + distances[:, 2:],
            ),
            axis=1,
        )

        points = centers[:, np.newaxis, :] + offsets[chosen]

        all_boxes.append(boxes)
        all_scores.append(scores[chosen])
        all_points.append(points)

    if not all_boxes:
        return []

    boxes = np.concatenate(all_boxes)
    scores = np.concatenate(all_scores)
    points = np.concatenate(all_points)

    selected = select_unique_faces(boxes, scores)

    coordinate_scale = np.array(
        [
            original_width / resized_width,
            original_height / resized_height,
        ],
        dtype=np.float32,
    )

    faces = []

    for index in selected:
        box = boxes[index].reshape(2, 2) * coordinate_scale
        landmarks = points[index] * coordinate_scale

        faces.append({
            "confidence": float(scores[index]),
            "box": box.reshape(-1).tolist(),
            "landmarks": landmarks.tolist(),
        })

    return faces

def detect_faces(image_path: Path):
    with Image.open(image_path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    return detect_faces_in_image(image)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    args = parser.parse_args()

    faces = detect_faces(args.image)

    print("Faces after NMS:", len(faces))

    for index, face in enumerate(faces, start=1):
        print(f"\nFace {index}")
        print("Confidence:", face["confidence"])
        print("Box:", face["box"])
        print("Landmarks:", face["landmarks"])


if __name__ == "__main__":
    main()