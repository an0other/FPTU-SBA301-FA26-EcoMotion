import argparse
import csv
import math
import re
from pathlib import Path


EXPECTED = ("CENTER", "POSITIVE", "NEGATIVE", "CENTER")
MIN_HOLD_SECONDS = 0.5
MAX_SAMPLE_GAP_SECONDS = 0.8


def classify_pose(yaw):
    if abs(yaw) <= 12:
        return "CENTER"
    if yaw >= 25:
        return "POSITIVE"
    if yaw <= -25:
        return "NEGATIVE"
    return "TRANSITION"


def frame_time(filename):
    match = re.fullmatch(
        r"frame_\d+_t(\d+\.\d+)\.png",
        filename,
    )

    if match is None:
        raise ValueError(f"Invalid frame filename: {filename}")

    return float(match.group(1))


def check_challenge_samples(samples):
    if not samples:
        raise ValueError("No pose samples provided")

    step = 0
    hold_started = None
    hold_samples = 0
    previous_time = None
    completed = []

    for sample in samples:
        timestamp = float(sample["timestamp"])

        if not math.isfinite(timestamp) or timestamp < 0:
            raise ValueError("Invalid sample timestamp")

        if previous_time is not None:
            gap = timestamp - previous_time

            if gap <= 0:
                raise ValueError("Samples must have increasing timestamps")

            if gap > MAX_SAMPLE_GAP_SECONDS:
                hold_started = None
                hold_samples = 0

        previous_time = timestamp

        yaw = sample["yaw"]
        valid = sample["faces"] == 1 and yaw is not None

        if valid:
            yaw = float(yaw)
            valid = math.isfinite(yaw)

        if not valid:
            hold_started = None
            hold_samples = 0
            continue

        pose = classify_pose(yaw)

        if pose != EXPECTED[step]:
            hold_started = None
            hold_samples = 0
            continue

        if hold_started is None:
            hold_started = timestamp

        hold_samples += 1
        held_seconds = timestamp - hold_started

        if hold_samples >= 2 and held_seconds >= MIN_HOLD_SECONDS:
            completed.append({
                "pose": EXPECTED[step],
                "from": hold_started,
                "to": timestamp,
            })

            step += 1
            hold_started = None
            hold_samples = 0

            if step == len(EXPECTED):
                break

    return completed

def check_challenge(report_path):
    with report_path.open(newline="", encoding="utf-8") as report:
        rows = list(csv.DictReader(report))

    samples = [
        {
            "timestamp": frame_time(row["frame"]),
            "faces": int(row["faces"]),
            "yaw": (
                float(row["yaw"])
                if row["yaw"].strip()
                else None
            ),
        }
        for row in rows
    ]

    return check_challenge_samples(samples)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report", type=Path)
    args = parser.parse_args()

    completed = check_challenge(args.report)

    for index, item in enumerate(completed, start=1):
        print(
            f"Step {index}: {item['pose']} completed "
            f"from {item['from']:.3f}s to {item['to']:.3f}s"
        )

    if len(completed) == len(EXPECTED):
        print("Challenge: PASS")
    else:
        print("Challenge: INCOMPLETE")
        print("Next expected pose:", EXPECTED[len(completed)])


if __name__ == "__main__":
    main()