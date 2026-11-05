"""Development-only request evidence and privacy-minimized pipeline trace."""
import json
import math
import os
import re
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path


_REASON_CODE = re.compile(r"[A-Z0-9_:-]{1,80}")
_SAFE_NAME = re.compile(r"[A-Z0-9_:-]{1,80}")
_STAGE_FOLDERS = {
    "VALIDATING_FILES": "01_validate_input",
    "LOCALIZING_AND_CHECKING_QUALITY": "02_localization_quality",
    "QUICK_QR_PAIR_CHECK": "03_qr_pair_precheck",
    "CHECKING_ORIENTATION_AND_TYPE": "04_orientation_document_type",
    "ANALYZING_IMAGE_SIGNALS": "05_image_analysis",
    "READING_FIELDS": "06_ocr",
    "COMPARING_OCR_AND_QR": "07_qr_comparison",
    "EXTRACTING_AND_VALIDATING_FIELDS": "08_field_extraction",
    "FINALIZING": "09_finalization",
    "COMPLETED": "10_completed",
}
_CHECK_STAGE_FOLDERS = {
    "FILE": "01_validate_input",
    "LOCALIZE": "02_localization_quality",
    "LOCALIZATION_FALLBACK": "02_localization_quality",
    "QUALITY": "02_localization_quality",
    "QUALITY_REGIONS": "02_localization_quality",
    "QR_PRECHECK": "03_qr_pair_precheck",
    "ORIENTATION": "04_orientation_document_type",
    "PAIR_PRECHECK": "04_orientation_document_type",
    "DOCUMENT_TYPE": "04_orientation_document_type",
    "IMAGE_ANALYSIS": "05_image_analysis",
    "OCR": "06_ocr",
    "TARGETED_FIELD_RESCUE": "06_ocr",
    "GEOMETRY_REUSE_FALLBACK": "06_ocr",
    "QR": "07_qr_comparison",
    "EXTRACTION": "08_field_extraction",
    "CONSISTENCY": "08_field_extraction",
}


class PipelineAudit:
    def __init__(self, environment="production", storage_root=None, enabled=None):
        if environment not in {"development", "production"}:
            raise ValueError("DOCUMENT_AI_ENVIRONMENT must be development or production")
        self.environment = environment
        self.storage_root = (
            Path(storage_root)
            if storage_root
            else Path(__file__).resolve().parents[3] / "runs"
        )
        setting = str(
            enabled
            if enabled is not None
            else os.getenv("DOCUMENT_AI_PIPELINE_TRACE", "1")
        ).strip().lower()
        if setting not in {"1", "0", "true", "false", "on", "off"}:
            raise ValueError("DOCUMENT_AI_PIPELINE_TRACE must be enabled or disabled")
        self.enabled = environment == "development" and setting in {"1", "true", "on"}
        self._lock = threading.Lock()

    def begin(self, front, back, source="read"):
        if not self.enabled:
            return None
        request_id = uuid.uuid4().hex
        request_dir = self._request_dir(request_id)
        input_dir = request_dir / "input"
        input_dir.mkdir(parents=True, exist_ok=True)
        input_files = []
        for side, content in (("front", front), ("back", back)):
            path = input_dir / f"{side}.bin"
            path.write_bytes(content)
            input_files.append(str(path.relative_to(self.storage_root)))
        self._append({
            "timestamp": self._timestamp(),
            "traceId": request_id,
            "event": "REQUEST_STARTED",
            "source": self._safe_source(source),
            "inputFiles": input_files,
        })
        return request_id

    def evidence_dir(self, request_id):
        if not self.enabled or request_id is None:
            return None
        return self._request_dir(request_id)

    def record_progress(self, request_id, progress):
        if not self.enabled or request_id is None:
            return
        stage = progress.get("stage")
        if not isinstance(stage, str) or not _SAFE_NAME.fullmatch(stage):
            stage = "UNKNOWN_STAGE"
        side = progress.get("side")
        if side not in {"FRONT", "BACK", "PAIR"}:
            side = "PAIR"
        elapsed = progress.get("elapsedMs")
        if not isinstance(elapsed, (int, float)) or not math.isfinite(elapsed):
            elapsed = None
        event = {
            "timestamp": self._timestamp(),
            "traceId": self._safe_request_id(request_id),
            "event": "PIPELINE_STAGE",
            "stage": stage,
            "side": side,
            "elapsedMs": elapsed,
        }
        folder = self._stage_dir(request_id, stage)
        self._append_stage_event(folder, event)
        self._append(event)

    def record_result(self, result, front, back, source="read", trace_id=None):
        if not self.enabled:
            return
        request_id = self._safe_request_id(trace_id or result.get("requestId"))
        request_dir = self._request_dir(request_id)
        if not (request_dir / "input" / "front.bin").exists():
            self._write_inputs(request_id, front, back)

        checks = []
        for check in result.get("checks", []):
            row = self._safe_check(check)
            checks.append(row)
            event = {
                "timestamp": self._timestamp(),
                "traceId": request_id,
                "event": "CHECK_COMPLETED",
                **row,
            }
            folder = self._request_dir(request_id) / "stages" / _CHECK_STAGE_FOLDERS.get(
                row["stage"], "08_field_extraction"
            )
            folder.mkdir(parents=True, exist_ok=True)
            self._append_stage_event(folder, event)
            self._append(event)

        report = result.get("pipelineReport") or {}
        summary = {
            "traceId": request_id,
            "requestId": self._safe_request_id(result.get("requestId")),
            "source": self._safe_source(source),
            "status": self._safe_value(result.get("status")),
            "documentType": self._safe_value(result.get("documentType")),
            "elapsedMs": self._finite_number(result.get("elapsedMs")),
            "pipelineStages": [
                {
                    "id": self._safe_value(stage.get("id")),
                    "status": self._safe_value(stage.get("status")),
                }
                for stage in report.get("stages", [])
                if isinstance(stage, dict)
            ],
            "checks": checks,
        }
        final_dir = self._stage_dir(request_id, "FINALIZING")
        (final_dir / "summary.json").write_text(
            json.dumps(summary, ensure_ascii=False, indent=2, allow_nan=False),
            encoding="utf-8",
        )
        self._append({
            "timestamp": self._timestamp(),
            "traceId": request_id,
            "requestId": summary["requestId"],
            "event": "REQUEST_COMPLETED",
            "source": summary["source"],
            "documentType": summary["documentType"],
            "status": summary["status"],
            "elapsedMs": summary["elapsedMs"],
        })

    def record_failure(self, request_id, error_type, front, back, source="read"):
        if not self.enabled:
            return
        safe_id = self._safe_request_id(request_id)
        request_dir = self._request_dir(safe_id)
        if not (request_dir / "input" / "front.bin").exists():
            self._write_inputs(safe_id, front, back)
        event = {
            "timestamp": self._timestamp(),
            "traceId": safe_id,
            "event": "REQUEST_FAILED",
            "source": self._safe_source(source),
            "errorType": self._safe_value(error_type),
        }
        (self._stage_dir(safe_id, "FINALIZING") / "failure.json").write_text(
            json.dumps(event, ensure_ascii=False, indent=2, allow_nan=False),
            encoding="utf-8",
        )
        self._append(event)

    def _write_inputs(self, request_id, front, back):
        input_dir = self._request_dir(request_id) / "input"
        input_dir.mkdir(parents=True, exist_ok=True)
        for side, content in (("front", front), ("back", back)):
            (input_dir / f"{side}.bin").write_bytes(content)

    def _request_dir(self, request_id):
        return self.storage_root / "requests" / self._safe_request_id(request_id)

    def _stage_dir(self, request_id, stage):
        name = _STAGE_FOLDERS.get(stage, "99_other")
        folder = self._request_dir(request_id) / "stages" / name
        folder.mkdir(parents=True, exist_ok=True)
        return folder

    def _append_stage_event(self, folder, event):
        line = json.dumps(event, ensure_ascii=False, allow_nan=False)
        with self._lock:
            with (folder / "events.jsonl").open("a", encoding="utf-8", newline="\n") as stream:
                stream.write(line + "\n")

    def _append(self, event):
        line = json.dumps(event, ensure_ascii=False, allow_nan=False)
        with self._lock:
            self.storage_root.mkdir(parents=True, exist_ok=True)
            with (self.storage_root / "pipeline.log").open("a", encoding="utf-8", newline="\n") as stream:
                stream.write(line + "\n")

    @staticmethod
    def _safe_check(check):
        reasons = [
            reason if isinstance(reason, str) and _REASON_CODE.fullmatch(reason) else "UNSAFE_REASON_OMITTED"
            for reason in check.get("reasons", [])
            if isinstance(reason, str)
        ]
        return {
            "side": check.get("side") if check.get("side") in {"FRONT", "BACK", "PAIR"} else "PAIR",
            "stage": (
                check.get("step")
                if isinstance(check.get("step"), str) and _SAFE_NAME.fullmatch(check["step"])
                else "UNKNOWN_STAGE"
            ),
            "status": PipelineAudit._safe_value(check.get("status")),
            "durationMs": PipelineAudit._finite_number(check.get("durationMs")),
            "reasonCodes": reasons,
        }

    @staticmethod
    def _safe_value(value):
        if isinstance(value, str) and _SAFE_NAME.fullmatch(value):
            return value
        return None

    @staticmethod
    def _safe_source(value):
        if isinstance(value, str) and re.fullmatch(r"[a-zA-Z0-9_:-]{1,80}", value):
            return value
        return "unknown"

    @staticmethod
    def _finite_number(value):
        return value if isinstance(value, (int, float)) and math.isfinite(value) else None

    @staticmethod
    def _timestamp():
        return datetime.now(timezone.utc).isoformat()

    @staticmethod
    def _safe_request_id(value):
        if isinstance(value, str) and re.fullmatch(r"[a-zA-Z0-9_-]{1,64}", value):
            return value
        return uuid.uuid4().hex
