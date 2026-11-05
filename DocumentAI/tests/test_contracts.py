from dataclasses import asdict
from document_ai.core import Check, Status, dumps


def test_unknown_does_not_become_pass():
    check = Check("FORENSICS", Status.INCONCLUSIVE, True)
    assert check.status != Status.PASS
    assert "score" not in asdict(check)


def test_metrics_are_not_shared():
    a = Check("A", Status.ERROR, True)
    b = Check("B", Status.SKIPPED, True)
    a.metrics["x"] = 1
    assert b.metrics == {}


def test_json_is_strict():
    assert 'null' in dumps({"x": float("nan")})
