import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from document_ai.reader.api import create_app
from test_v17_contract import fake_reader, raw


TEST_API_KEY = "test-api-key-for-document-ai-tests-0001"


def client_for(reader, **kwargs):
    return TestClient(
        create_app(reader, api_key=TEST_API_KEY, **kwargs),
        headers={"X-API-Key": TEST_API_KEY},
    )


def payload():
    return {
        "front": ("front.jpg", raw("white"), "image/jpeg"),
        "back": ("back.jpg", raw("gray"), "image/jpeg"),
    }


def test_inference_and_job_results_require_service_key(monkeypatch):
    calls = []
    reader = fake_reader(monkeypatch)
    original = reader.read

    def read(*args, **kwargs):
        calls.append(True)
        return original(*args, **kwargs)

    reader.read = read
    app = create_app(reader, api_key=TEST_API_KEY)
    with TestClient(app) as client:
        missing = client.post("/v1/documents/read", files=payload())
        assert missing.status_code == 401
        assert missing.json()["detail"] == "Missing X-API-Key header"
        assert client.post("/v1/documents/jobs", files=payload()).status_code == 401
        assert client.get("/v1/documents/jobs/unknown").status_code == 401
        assert calls == []
    with TestClient(app, headers={"X-API-Key": "wrong"}) as client:
        invalid = client.post("/v1/documents/read", files=payload())
        assert invalid.status_code == 401
        assert invalid.json()["detail"] == "Invalid X-API-Key"
        assert calls == []


def test_missing_or_short_api_key_fails_closed(monkeypatch):
    monkeypatch.delenv("DOCUMENT_AI_API_KEY", raising=False)
    with pytest.raises(ValueError, match="DOCUMENT_AI_API_KEY"):
        create_app(reader=object())
    with pytest.raises(ValueError, match="DOCUMENT_AI_API_KEY"):
        create_app(reader=object(), api_key="short")


def test_openapi_documents_service_key_for_protected_routes(monkeypatch):
    with client_for(fake_reader(monkeypatch)) as client:
        schema = client.get("/openapi.json").json()

    assert "APIKeyHeader" in schema["components"]["securitySchemes"]
    assert schema["components"]["securitySchemes"]["APIKeyHeader"]["name"] == "X-API-Key"
    assert schema["components"]["securitySchemes"]["APIKeyHeader"]["in"] == "header"
    for route, method in (
        ("/v1/documents/read", "post"),
        ("/v1/documents/jobs", "post"),
        ("/v1/documents/jobs/{job_id}", "get"),
    ):
        assert schema["paths"][route][method]["security"] == [{"APIKeyHeader": []}]


def test_development_stores_inputs_and_redacted_pipeline_trace(tmp_path, monkeypatch):
    reader = fake_reader(monkeypatch)
    original = reader.read

    def read(*args, **kwargs):
        result = original(*args, **kwargs)
        result["pipelineReport"] = {
            "stages": [{"id": "OCR_QR", "status": "PASS"}],
        }
        return result

    reader.read = read
    with client_for(reader, environment="development", storage_root=tmp_path) as client:
        response = client.post(
            "/v1/documents/read",
            files=payload(),
            data={"document_type": "VN_DRIVING_LICENSE"},
        )

    assert response.status_code == 200
    lines = (tmp_path / "pipeline.log").read_text(encoding="utf-8").splitlines()
    events = [json.loads(line) for line in lines]
    completed = next(event for event in events if event["event"] == "REQUEST_COMPLETED")
    request_dir = tmp_path / "requests" / completed["traceId"]
    assert (request_dir / "input" / "front.bin").read_bytes() == raw("white")
    assert (request_dir / "input" / "back.bin").read_bytes() == raw("gray")
    assert (request_dir / "stages" / "09_finalization" / "summary.json").is_file()
    summary = json.loads((request_dir / "stages" / "09_finalization" / "summary.json").read_text(encoding="utf-8"))
    assert summary["pipelineStages"][0]["id"] == "OCR_QR"
    assert any(event["event"] == "CHECK_COMPLETED" for event in events)
    trace = "\n".join(lines)
    assert "Nguyễn An" not in trace
    assert "079123456789" not in trace
    assert "qrData" not in trace


def test_production_does_not_persist_inputs_or_pipeline_log(tmp_path, monkeypatch):
    reader = fake_reader(monkeypatch)
    with client_for(reader, environment="production", storage_root=tmp_path) as client:
        response = client.post(
            "/v1/documents/read",
            files=payload(),
            data={"document_type": "VN_DRIVING_LICENSE"},
        )

    assert response.status_code == 200
    assert not Path(tmp_path).exists() or not list(Path(tmp_path).iterdir())


def test_development_job_saves_inputs_and_trace_after_completion(tmp_path, monkeypatch):
    import time

    with client_for(
        fake_reader(monkeypatch),
        environment="development",
        storage_root=tmp_path,
    ) as client:
        submitted = client.post("/v1/documents/jobs", files=payload())
        assert submitted.status_code == 202
        for _ in range(100):
            job = client.get(submitted.json()["pollUrl"]).json()
            if job["state"] in {"COMPLETED", "FAILED"}:
                break
            time.sleep(0.01)

    assert job["state"] == "COMPLETED"
    trace = (tmp_path / "pipeline.log").read_text(encoding="utf-8")
    assert '"source": "job:' in trace
    events = [json.loads(line) for line in trace.splitlines()]
    completed = next(event for event in events if event["event"] == "REQUEST_COMPLETED")
    assert (tmp_path / "requests" / completed["traceId"] / "input" / "front.bin").read_bytes() == raw("white")


def test_development_records_live_stage_progress_and_passes_evidence_dir(tmp_path, monkeypatch):
    reader = fake_reader(monkeypatch)
    original = reader.read

    def read(front, back, expected_type="AUTO", evidence_dir=None, progress=None):
        assert evidence_dir is not None
        progress({"stage": "VALIDATING_FILES", "side": "PAIR", "elapsedMs": 1.5})
        artifact = Path(evidence_dir) / "stages" / "01_validate_input" / "validation.json"
        artifact.parent.mkdir(parents=True, exist_ok=True)
        artifact.write_text('{"imageCount":2}', encoding="utf-8")
        return original(front, back, expected_type)

    reader.read = read
    reader.supports_progress = True
    with client_for(reader, environment="development", storage_root=tmp_path) as client:
        response = client.post("/v1/documents/read", files=payload())

    assert response.status_code == 200
    events = [json.loads(line) for line in (tmp_path / "pipeline.log").read_text(encoding="utf-8").splitlines()]
    stage_event = next(event for event in events if event["event"] == "PIPELINE_STAGE")
    request_dir = tmp_path / "requests" / stage_event["traceId"]
    stage_dir = request_dir / "stages" / "01_validate_input"
    assert json.loads((stage_dir / "validation.json").read_text(encoding="utf-8")) == {"imageCount": 2}
    assert json.loads((stage_dir / "events.jsonl").read_text(encoding="utf-8").splitlines()[0])["stage"] == "VALIDATING_FILES"


def test_development_pipeline_trace_can_be_disabled(tmp_path, monkeypatch):
    monkeypatch.setenv("DOCUMENT_AI_PIPELINE_TRACE", "off")
    with client_for(fake_reader(monkeypatch), environment="development", storage_root=tmp_path) as client:
        response = client.post("/v1/documents/read", files=payload())
    assert response.status_code == 200
    assert not list(tmp_path.iterdir())


def test_staged_localization_saves_card_mask_and_crop_evidence(tmp_path):
    from PIL import Image
    from document_ai.core import StepOutput, Status
    from document_ai.reader.staged import save_evidence

    image = Image.new("RGB", (160, 100), "white")
    points = [[20, 15], [140, 15], [140, 85], [20, 85]]
    save_evidence({
        "side": "FRONT",
        "original": image,
        "aligned": image.crop((20, 15, 140, 85)),
        "quality": StepOutput(Status.PASS),
        "geometry": {
            "status": "LOCATED",
            "method": "test",
            "corners": points,
            "image": image,
        },
    }, tmp_path)

    folder = tmp_path / "stages" / "02_localization_quality" / "front"
    assert (folder / "original.png").is_file()
    assert (folder / "card.png").is_file()
    assert (folder / "localization-overlay.jpg").is_file()
    assert (folder / "card-mask.png").is_file()
    mask = Image.open(folder / "card-mask.png")
    assert mask.getbbox() == (20, 15, 141, 86)


def test_upload_size_limit_rejects_before_inference(tmp_path, monkeypatch):
    monkeypatch.setenv("DOCUMENT_AI_MAX_UPLOAD_BYTES", "10")
    with client_for(fake_reader(monkeypatch), environment="development", storage_root=tmp_path) as client:
        response = client.post("/v1/documents/read", files=payload())
    assert response.status_code == 413
    assert not list(tmp_path.iterdir())
