from __future__ import annotations
import hashlib
import json
import math
from dataclasses import asdict, dataclass, field
from enum import StrEnum
from pathlib import Path
from typing import Any, Protocol


class Status(StrEnum):
    PASS = "PASS"
    FAIL = "FAIL"
    INCONCLUSIVE = "INCONCLUSIVE"
    ERROR = "ERROR"
    SKIPPED = "SKIPPED"
    NOT_APPLICABLE = "NOT_APPLICABLE"


@dataclass
class Check:
    check: str
    status: Status
    required: bool
    image_id: str | None = None
    reasons: list[str] = field(default_factory=list)
    metrics: dict[str, Any] = field(default_factory=dict)
    evidence: list[str] = field(default_factory=list)
    duration_ms: float = 0.0
    implementation: str = "document-ai/0.13.0"


@dataclass
class InputImage:
    side: str
    content: bytes
    image_id: str

    @property
    def sha256(self):
        return hashlib.sha256(self.content).hexdigest()


@dataclass
class StepOutput:
    status: Status
    reasons: list[str] = field(default_factory=list)
    metrics: dict[str, Any] = field(default_factory=dict)
    payload: dict[str, Any] = field(default_factory=dict)
    evidence: dict[str, bytes] = field(default_factory=dict)
    implementation: str = "document-ai/0.13.0"


class OcrEngine(Protocol):
    def extract(self, image: Any) -> list[dict[str, Any]]: ...


class ImageAnalyzer(Protocol):
    def analyze(self, raw: bytes, image: Any, config: dict[str, Any]) -> StepOutput: ...


def clean_json(value):
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if isinstance(value, dict):
        return {str(k): clean_json(v) for k,v in value.items()}
    if isinstance(value, (list, tuple)):
        return [clean_json(v) for v in value]
    if isinstance(value, StrEnum):
        return value.value
    return value


def dumps(value):
    return json.dumps(clean_json(value), ensure_ascii=False, indent=2, allow_nan=False)


def load_profile(path: str | Path):
    path = Path(path).resolve()
    p = json.loads(path.read_text(encoding="utf-8"))
    if p.get("document_type") not in {"VN_DRIVING_LICENSE", "VN_ID_CARD"}:
        raise ValueError("Unsupported document_type")
    if not p.get("id") or not p.get("version"):
        raise ValueError("Profile id and version required")
    sides=p.get("required_sides", [])
    if not sides or len(sides)!=len(set(sides)) or any(x not in {"FRONT","BACK"} for x in sides):
        raise ValueError("required_sides must contain unique FRONT/BACK")
    if p.get("forensics",{}).get("mode","disabled") not in {"disabled","experimental","required"}:
        raise ValueError("Invalid forensics mode")
    if p.get("forensics",{}).get("mode")=="required" and not p["forensics"].get("thresholds_validated",False):
        raise ValueError("Required forensics needs validated thresholds")
    for spec in p.get("fields",[]):
        if spec["side"] not in sides or len(spec["roi"])!=4:
            raise ValueError("Field side/roi invalid")
        x1,y1,x2,y2=spec["roi"]
        if not 0<=x1<x2<=1 or not 0<=y1<y2<=1:
            raise ValueError("ROI must be normalized x1,y1,x2,y2")
    p["_base"] = str(path.parent)
    p["_fingerprint"] = hashlib.sha256(path.read_bytes()).hexdigest()
    return p
