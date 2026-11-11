import argparse
from pathlib import Path

import av
import numpy as np

from identity_verification.config import get_settings


def validate_video_container(video_path: Path):
    allowed_formats = {"mov", "mp4", "matroska", "webm"}

    try:
        with video_path.open("rb") as source:
            with av.open(source, mode="r") as container:
                detected_formats = set(container.format.name.split(","))

                if not detected_formats.intersection(allowed_formats):
                    raise ValueError(
                        "Expected an MP4/MOV or WebM/Matroska video container"
                    )

                if len(container.streams.video) != 1:
                    raise ValueError(
                        "Expected exactly one video stream"
                    )

                frames = container.decode(video=0)

                if next(frames, None) is None:
                    raise ValueError("Video contains no decodable frames")

                if next(frames, None) is None:
                    raise ValueError(
                        "Video must contain more than one frame"
                    )

    except (av.FFmpegError, OSError) as error:
        raise ValueError(
            "Could not read a valid video container"
        ) from error


def iter_video_frames(video_path: Path, sample_fps: float = 2.0):
    settings = get_settings()

    if not video_path.is_file():
        raise FileNotFoundError(f"Video not found: {video_path}")

    if not np.isfinite(sample_fps) or sample_fps <= 0:
        raise ValueError("sample_fps must be a positive number")

    validate_video_container(video_path)
    try:
        with av.open(str(video_path), mode="r") as container:
            stream = container.streams.video[0]
            fps = float(stream.average_rate) if stream.average_rate else None
            if fps is not None and (not np.isfinite(fps) or fps <= 0):
                fps = None
            first_time = None
            previous_time = None
            sampled_count = 0
            frame_count = 0
            next_sample_time = 0.0
            for frame_index, frame in enumerate(container.decode(video=0)):
                if frame_index >= settings.max_video_frames:
                    raise ValueError("Video contains too many frames")
                if frame.width * frame.height > settings.max_video_pixels:
                    raise ValueError(
                        f"Video resolution exceeds {settings.max_video_pixels} pixels"
                    )
                frame_time = frame.time
                if frame_time is None:
                    if fps is None:
                        raise ValueError("Could not determine video timestamps")
                    frame_time = frame_index / fps
                frame_time = float(frame_time)
                if not np.isfinite(frame_time):
                    raise ValueError("Invalid video timestamp")
                if first_time is None:
                    first_time = frame_time
                timestamp = frame_time - first_time
                if previous_time is not None and timestamp < previous_time:
                    raise ValueError("Video timestamps must be ordered")
                previous_time = timestamp
                if timestamp >= settings.max_video_seconds:
                    raise ValueError(
                        f"Video must not exceed {settings.max_video_seconds:g} seconds"
                    )
                frame_count += 1
                if frame_count > settings.max_video_fps * (timestamp + 1):
                    raise ValueError(f"Video FPS exceeds {settings.max_video_fps:g}")
                if timestamp + 1e-9 >= next_sample_time:
                    sampled_count += 1
                    next_sample_time = sampled_count / sample_fps
                    yield {
                        "frame_index": frame_index,
                        "timestamp": timestamp,
                        "image": frame.to_image().convert("RGB"),
                    }
            if frame_count < 2 or not previous_time:
                raise ValueError("Video must contain multiple frames over time")
    except (av.FFmpegError, OSError) as error:
        raise ValueError("Could not decode video") from error


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("video", type=Path)
    parser.add_argument("--sample-fps", type=float, default=2.0)
    args = parser.parse_args()

    count = 0

    for sample in iter_video_frames(args.video, args.sample_fps):
        print(
            f"Frame {sample['frame_index']:06d}, "
            f"time={sample['timestamp']:.3f}s, "
            f"size={sample['image'].size}"
        )
        count += 1

    print("Sampled frames:", count)
    print("No frame images were saved")


if __name__ == "__main__":
    main()
