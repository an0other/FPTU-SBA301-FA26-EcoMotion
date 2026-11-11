import argparse
import csv
import json
import math
from pathlib import Path

import numpy as np

from identity_verification.check_challenge import classify_pose
from PIL import Image, ImageOps

from identity_verification.face_alignment import align_face_in_image
from identity_verification.face_matching import (
    create_session,
    extract_embedding_from_image,
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("cccd", type=Path)
    parser.add_argument("report", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)

    session = create_session()

    with Image.open(args.cccd) as source:
        cccd_image = ImageOps.exif_transpose(source).convert("RGB")

    aligned_cccd = align_face_in_image(cccd_image)
    reference = extract_embedding_from_image(session, aligned_cccd)

    with args.report.open(newline="", encoding="utf-8") as report:
        rows = list(csv.DictReader(report))

    results = []

    for row in rows:
        if int(row["faces"]) != 1 or not row["yaw"].strip():
            continue

        yaw = float(row["yaw"])

        if not math.isfinite(yaw) or classify_pose(yaw) != "CENTER":
            continue

        filename = row["frame"]

        if Path(filename).name != filename:
            raise ValueError("Frame name must not contain a directory")

        frame_path = args.report.parent / filename

        with Image.open(frame_path) as source:
            frame_image = ImageOps.exif_transpose(source).convert("RGB")

        try:
            aligned = align_face_in_image(frame_image)
        except ValueError as error:
            results.append({
                "frame": filename,
                "status": "UNUSABLE",
                "reason": str(error),
            })
            print(f"{filename}: unusable, {error}")
            continue

        embedding = extract_embedding_from_image(session, aligned)

        score = float(
            np.clip(np.dot(reference, embedding), -1.0, 1.0)
        )

        results.append({
            "frame": filename,
            "status": "SCORED",
            "yaw": yaw,
            "similarity": score,
        })

        print(f"{filename}: similarity={score:.6f}")

    scores = [
        item["similarity"]
        for item in results
        if item["status"] == "SCORED"
    ]

    summary = {
        "candidateFrames": len(results),
        "scoredFrames": len(scores),
        "unusableFrames": len(results) - len(scores),
        "minimum": min(scores) if scores else None,
        "median": float(np.median(scores)) if scores else None,
        "maximum": max(scores) if scores else None,
        "decision": "NOT_EVALUATED",
        "frames": results,
    }

    report_path = args.output / "matching_report.json"
    report_path.write_text(
        json.dumps(summary, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    print("\nScored frames:", len(scores))

    if scores:
        print(f"Minimum: {summary['minimum']:.6f}")
        print(f"Median: {summary['median']:.6f}")
        print(f"Maximum: {summary['maximum']:.6f}")
    else:
        print("No usable frontal frames")

    print("Saved report:", report_path)


if __name__ == "__main__":
    main()