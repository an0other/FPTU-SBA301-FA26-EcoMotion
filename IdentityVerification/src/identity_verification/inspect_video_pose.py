import argparse
from contextlib import closing
from pathlib import Path

from identity_verification.check_challenge import (
    EXPECTED,
    check_challenge_samples,
)
from identity_verification.inspect_head_pose import (
    create_landmarker,
    inspect_pose_in_image,
)
from identity_verification.video_reader import iter_video_frames


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("video", type=Path)
    args = parser.parse_args()

    count = 0
    pose_samples = []

    with create_landmarker() as landmarker:
        with closing(iter_video_frames(args.video, sample_fps=2.0)) as frames:
            for sample in frames:
                pose = inspect_pose_in_image(
                    landmarker,
                    sample["image"],
                )
                pose_samples.append({
                    "timestamp": sample["timestamp"],
                    "faces": pose["faces"],
                    "yaw": pose["yaw"],
                })

                yaw_text = (
                    "unavailable"
                    if pose["yaw"] is None
                    else f"{pose['yaw']:+.1f}"
                )

                print(
                    f"Frame {sample['frame_index']:06d}, "
                    f"time={sample['timestamp']:.3f}s, "
                    f"faces={pose['faces']}, "
                    f"yaw={yaw_text}"
                )

                count += 1

    completed = check_challenge_samples(pose_samples)

    for index, item in enumerate(completed, start=1):
        print(
            f"Step {index}: {item['pose']} "
            f"from {item['from']:.3f}s to {item['to']:.3f}s"
        )

    if len(completed) == len(EXPECTED):
        print("Challenge: PASS")
    else:
        print("Challenge: INCOMPLETE")
        print("Next expected pose:", EXPECTED[len(completed)])
        
    print("Processed samples:", count)
    print("No intermediate images or CSV were saved")


if __name__ == "__main__":
    main()