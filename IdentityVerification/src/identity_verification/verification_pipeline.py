import argparse
import json
from contextlib import closing
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from identity_verification.check_challenge import (
    EXPECTED,
    check_challenge_samples,
    classify_pose,
)
from identity_verification.face_alignment import align_face_in_image
from identity_verification.face_matching import (
    create_session,
    assess_match,
    model_provenance,
    extract_embedding_from_image,
)
from identity_verification.inspect_head_pose import (
    create_landmarker,
    inspect_pose_in_image,
)
from identity_verification.video_reader import iter_video_frames


def verify_video(cccd_path: Path, video_path: Path, progress=None):
    def emit(stage, **details):
        if progress:
            progress(dict(stage=stage, **details))
    emit('LOADING_MODELS')
    session = create_session()
    emit('REFERENCE_FACE')

    with Image.open(cccd_path) as source:
        reference_image = ImageOps.exif_transpose(source).convert("RGB")

    reference_face = align_face_in_image(reference_image)
    reference = extract_embedding_from_image(session, reference_face)

    pose_samples = []
    matching_frames = []

    emit('READING_VIDEO', sampledFrames=0)
    with create_landmarker() as landmarker:
        with closing(iter_video_frames(video_path, sample_fps=2.0)) as frames:
            for sample in frames:
                emit('ANALYZING_VIDEO', sampledFrames=len(pose_samples), videoSeconds=pose_samples[-1]['timestamp'] if pose_samples else 0)
                image = sample["image"]
                pose = inspect_pose_in_image(landmarker, image)

                pose_samples.append({
                    "timestamp": sample["timestamp"],
                    "faces": pose["faces"],
                    "yaw": pose["yaw"],
                })

                if pose["faces"] != 1 or pose["yaw"] is None:
                    continue

                if classify_pose(pose["yaw"]) != "CENTER":
                    continue

                item = {
                    "frameIndex": sample["frame_index"],
                    "timestamp": sample["timestamp"],
                    "yaw": pose["yaw"],
                }

                try:
                    aligned = align_face_in_image(image)
                except ValueError as error:
                    item.update({
                        "status": "UNUSABLE",
                        "reason": str(error),
                    })
                    matching_frames.append(item)
                    continue

                embedding = extract_embedding_from_image(session, aligned)
                similarity = float(
                    np.clip(np.dot(reference, embedding), -1.0, 1.0)
                )

                item.update({
                    "status": "SCORED",
                    "similarity": similarity,
                })
                matching_frames.append(item)

    emit('CHECKING_CHALLENGE', sampledFrames=len(pose_samples))
    completed = check_challenge_samples(pose_samples)

    scores = [
        item["similarity"]
        for item in matching_frames
        if item["status"] == "SCORED"
    ]
    from identity_verification.config import get_settings
    settings=get_settings()
    emit('ASSESSING_FACE_SIMILARITY',sampledFrames=len(pose_samples))
    assessment=assess_match(scores,settings.face_match_low_threshold,settings.face_match_high_threshold,
        settings.face_match_min_frames,settings.face_match_calibration_id or 'face-match-observation-v1',model_provenance()['sha256'])
    challenge_passed = len(completed) == len(EXPECTED)
    reasons = []

    if not challenge_passed:
        if pose_samples and all(item["faces"] == 0 for item in pose_samples):
            reasons.append("NO_FACE_DETECTED")
        elif pose_samples and all(item["faces"] != 1 for item in pose_samples):
            reasons.append("NO_SINGLE_FACE_SEQUENCE")
        elif not any(item["faces"] == 1 and item["yaw"] is not None for item in pose_samples):
            reasons.append("HEAD_POSE_UNAVAILABLE")
        else:
            reasons.append("MOTION_CHALLENGE_INCOMPLETE")

    if not scores:
        reasons.append("NO_USABLE_FRONTAL_FACE")

    if assessment['status']=='INSUFFICIENT_EVIDENCE':
        reasons.append('INSUFFICIENT_FACE_SAMPLES')
    workflow_status = (
        "RETRY_REQUIRED"
        if reasons
        else "READY_FOR_REVIEW"
    )

    emit('FINALIZING', sampledFrames=len(pose_samples))
    return {
        "sampledFrames": len(pose_samples),
        "motionChallenge": {
            "status": "PASS" if challenge_passed else "INCOMPLETE",
            "expected": list(EXPECTED),
            "completed": completed,
        },
        "faceMatching": {
            "candidateFrames": len(matching_frames),
            "scoredFrames": len(scores),
            "unusableFrames": len(matching_frames) - len(scores),
            "minimum": min(scores) if scores else None,
            "median": float(np.median(scores)) if scores else None,
            "maximum": max(scores) if scores else None,
            "frames": matching_frames,
        },
        "workflow": {
            "status": workflow_status,
            "reasons": reasons,
        },
        "matchAssessment": assessment,
        "identityDecision": "NOT_EVALUATED",
        "automaticApproval": False,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("cccd", type=Path)
    parser.add_argument("video", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.output.exists():
        raise FileExistsError(f"Output already exists: {args.output}")

    result = verify_video(args.cccd, args.video)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(result, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    print("Sampled frames:", result["sampledFrames"])
    print("Motion challenge:", result["motionChallenge"]["status"])
    print("Scored frames:", result["faceMatching"]["scoredFrames"])
    print("Median similarity:", result["faceMatching"]["median"])
    print("Identity decision:", result["identityDecision"])
    print("Saved result:", args.output)


if __name__ == "__main__":
    main()
