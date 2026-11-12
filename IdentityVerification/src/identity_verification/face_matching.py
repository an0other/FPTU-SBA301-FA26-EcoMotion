import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image
from functools import lru_cache

@lru_cache(maxsize=1)
def model_provenance():
    matches = list((Path(__file__).resolve().parents[2] / 'models' / 'buffalo_sc').rglob('w600k_mbf.onnx'))
    if len(matches) != 1:
        raise RuntimeError('Expected exactly one face embedding model')
    return {'name':'w600k_mbf.onnx','sha256':hashlib.sha256(matches[0].read_bytes()).hexdigest()}


def assess_match(scores, low=None, high=None, minimum_frames=3, policy_version='face-match-observation-v1', model_sha256=None):
    if any(not math.isfinite(value) or not -1 <= value <= 1 for value in scores):
        raise ValueError('Invalid cosine score')
    if (low is None) != (high is None) or low is not None and not (-1 <= low < high <= 1):
        raise ValueError('Invalid threshold pair')
    score = float(np.median(scores)) if scores else None
    if not scores:
        status, reasons = 'NO_USABLE_FACE', ['NO_USABLE_FRONTAL_FACE']
    elif len(scores) < minimum_frames:
        status, reasons = 'INSUFFICIENT_EVIDENCE', ['INSUFFICIENT_FACE_SAMPLES']
    elif low is None:
        status, reasons = 'NOT_CALIBRATED', ['MATCH_THRESHOLD_NOT_CONFIGURED']
    elif score >= high:
        status, reasons = 'LIKELY_MATCH', []
    elif score < low:
        status, reasons = 'LOW_SIMILARITY', ['LOW_FACE_SIMILARITY']
    else:
        status, reasons = 'INCONCLUSIVE', ['MATCH_SCORE_IN_REVIEW_RANGE']
    return dict(status=status,score=score,lowThreshold=low,highThreshold=high,
        comparedFrames=len(scores),minimumFrames=minimum_frames,metric='cosine_similarity',
        modelSha256=model_sha256,policyVersion=policy_version,
        calibrationStatus='NOT_CALIBRATED' if low is None else 'EXPERIMENTAL',reasons=reasons)


def evaluate_scores(cases, low, high):
    if not (-1 <= low < high <= 1):
        raise ValueError('Invalid threshold pair')
    ids=set();same=[];different=[]
    for case in cases:
        identifier=case.get('captureId');score=case.get('score');label=case.get('samePerson')
        if not isinstance(identifier,str) or not identifier or identifier in ids or type(label) is not bool or type(score) not in (int,float) or not math.isfinite(score) or not -1 <= score <= 1:
            raise ValueError('Each case needs a unique captureId, explicit boolean label and valid video-level score')
        ids.add(identifier);(same if label else different).append(score)
    if not same or not different:
        raise ValueError('Both same-person and different-person labeled cases are required; one person is not sufficient for threshold calibration')
    total=len(same)+len(different)
    return dict(schemaVersion='face-match-evaluation/1',lowThreshold=low,highThreshold=high,
        samePersonCases=len(same),differentPersonCases=len(different),
        falseMatchRateAtHigh=sum(v>=high for v in different)/len(different),
        falseNonMatchRateAtHigh=sum(v<high for v in same)/len(same),
        lowSimilarityRateOnSamePerson=sum(v<low for v in same)/len(same),
        reviewRate=sum(low<=v<high for v in same+different)/total,
        calibrationStatus='EXPERIMENTAL',automaticApproval=False,
        limitation='Observed rates on supplied labeled cases only; not a production calibration or liveness assessment')


@lru_cache(maxsize=1)
def create_session():
    model_root = Path(__file__).resolve().parents[2] / "models" / "buffalo_sc"
    matches = list(model_root.rglob("w600k_mbf.onnx"))

    if len(matches) != 1:
        raise RuntimeError("Expected exactly one w600k_mbf.onnx")

    from identity_verification.config import get_settings
    expected=get_settings().face_match_model_sha256
    if expected and expected != model_provenance()['sha256']:
        raise RuntimeError('Face model checksum does not match configured threshold policy')
    return ort.InferenceSession(
        str(matches[0]),
        providers=["CPUExecutionProvider"],
    )


def extract_embedding_from_image(session, image: Image.Image):
    image = image.convert("RGB")

    if image.size != (112, 112):
        raise ValueError(
            f"Expected aligned image 112x112, received {image.size}"
        )

    pixels = np.asarray(image, dtype=np.float32)
    pixels = (pixels - 127.5) / 127.5

    tensor = pixels.transpose(2, 0, 1)[np.newaxis, ...]
    tensor = np.ascontiguousarray(tensor, dtype=np.float32)

    output = session.run(
        None,
        {session.get_inputs()[0].name: tensor},
    )[0]

    embedding = np.asarray(output, dtype=np.float32).reshape(-1)

    if embedding.size != 512 or not np.isfinite(embedding).all():
        raise ValueError("Model returned an invalid embedding")

    length = float(np.linalg.norm(embedding))

    if length < 1e-8:
        raise ValueError("Model returned a zero-length embedding")

    return embedding / length

def extract_embedding(session, image_path: Path):
    with Image.open(image_path) as source:
        image = source.convert("RGB")

    return extract_embedding_from_image(session, image)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("first_image", type=Path, nargs="?")
    parser.add_argument("second_image", type=Path, nargs="?")
    parser.add_argument('--evaluate',type=Path)
    parser.add_argument('--low',type=float)
    parser.add_argument('--high',type=float)
    args = parser.parse_args()
    if args.evaluate:
        if args.low is None or args.high is None:
            parser.error('--evaluate requires explicit --low and --high')
        data=json.loads(args.evaluate.read_text(encoding='utf-8'))
        provenance=model_provenance()
        if data.get('modelSha256') != provenance['sha256']:
            parser.error('Evaluation scores must declare the exact local model SHA256')
        report=evaluate_scores(data.get('cases',[]),args.low,args.high)
        report['modelSha256']=provenance['sha256']
        print(json.dumps(report,indent=2))
        return
    if args.first_image is None or args.second_image is None:
        parser.error('Provide two aligned images or --evaluate')

    session = create_session()

    first = extract_embedding(session, args.first_image)
    second = extract_embedding(session, args.second_image)

    similarity = float(np.clip(np.dot(first, second), -1.0, 1.0))

    print("Embedding dimensions:", first.size)
    print(f"Cosine similarity: {similarity:.6f}")
    print("Decision: not evaluated; threshold not calibrated")


if __name__ == "__main__":
    main()