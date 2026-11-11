import argparse
import json
import subprocess
import sys
from pathlib import Path

from identity_verification.check_challenge import EXPECTED, check_challenge


def run_module(module, *arguments):
    command = [
        sys.executable,
        "-m",
        f"identity_verification.{module}",
        *map(str, arguments),
    ]

    subprocess.run(command, check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("cccd", type=Path)
    parser.add_argument("video", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if not args.cccd.is_file():
        raise FileNotFoundError(f"Reference image not found: {args.cccd}")

    if not args.video.is_file():
        raise FileNotFoundError(f"Video not found: {args.video}")

    args.output.mkdir(parents=True, exist_ok=False)

    frames_dir = args.output / "frames"
    matching_dir = args.output / "matching"

    print("\nStep 1: Sample video frames", flush=True)

    run_module(
        "sample_video",
        args.video,
        "--output",
        frames_dir,
        "--sample-fps",
        "2",
    )

    print("\nStep 2: Estimate head pose", flush=True)

    run_module("inspect_head_pose", frames_dir)

    pose_report = frames_dir / "head_pose.csv"
    completed = check_challenge(pose_report)
    challenge_passed = len(completed) == len(EXPECTED)

    print("\nStep 3: Compare frontal faces", flush=True)

    run_module(
        "match_video_frames",
        args.cccd,
        pose_report,
        "--output",
        matching_dir,
    )

    matching_report = json.loads(
        (matching_dir / "matching_report.json").read_text(
            encoding="utf-8"
        )
    )

    result = {
        "motionChallenge": {
            "status": "PASS" if challenge_passed else "INCOMPLETE",
            "expected": list(EXPECTED),
            "completed": completed,
        },
        "faceMatching": matching_report,
        "identityDecision": "NOT_EVALUATED",
        "automaticApproval": False,
    }

    result_path = args.output / "result.json"
    result_path.write_text(
        json.dumps(result, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    print("\nMotion challenge:", result["motionChallenge"]["status"])
    print("Scored frames:", matching_report["scoredFrames"])
    print("Median similarity:", matching_report["median"])
    print("Identity decision:", result["identityDecision"])
    print("Saved result:", result_path)


if __name__ == "__main__":
    main()