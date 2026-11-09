import argparse
from pathlib import Path

import cv2
import numpy as np
from PIL import Image


def sample_video(video_path: Path, output_dir: Path, sample_fps: float):
    if not video_path.is_file():
        raise FileNotFoundError(f"Video not found: {video_path}")

    if not np.isfinite(sample_fps) or sample_fps <= 0:
        raise ValueError("sample_fps must be a positive number")

    capture = cv2.VideoCapture(str(video_path))

    try:
        if not capture.isOpened():
            raise ValueError("Could not open video")

        fps = capture.get(cv2.CAP_PROP_FPS)

        if not np.isfinite(fps) or fps <= 0:
            raise ValueError("Could not determine video FPS")

        output_dir.mkdir(parents=True, exist_ok=True)

        frame_index = 0
        saved_count = 0
        next_sample_time = 0.0
        sample_interval = 1.0 / sample_fps

        print(f"Reported video FPS: {fps:.3f}")

        while True:
            success, frame = capture.read()

            if not success:
                break

            timestamp = frame_index / fps

            if timestamp + 1e-9 >= next_sample_time:
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                image = Image.fromarray(rgb)

                filename = (
                    f"frame_{frame_index:06d}"
                    f"_t{timestamp:.3f}.png"
                )
                image.save(output_dir / filename)

                saved_count += 1
                next_sample_time = saved_count * sample_interval

            frame_index += 1

        if frame_index == 0:
            raise ValueError("No video frames could be decoded")

        print("Decoded frames:", frame_index)
        print("Saved samples:", saved_count)
        print(f"Estimated duration: {frame_index / fps:.3f} seconds")
        print("Output directory:", output_dir)

    finally:
        capture.release()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("video", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sample-fps", type=float, default=2.0)
    args = parser.parse_args()

    sample_video(args.video, args.output, args.sample_fps)


if __name__ == "__main__":
    main()