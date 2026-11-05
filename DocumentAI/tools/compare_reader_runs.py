"""Compare saved reader experiment summaries and report OCR value changes."""


def compare(baseline, experiment):
    baseline_summary = baseline.get("summary", {})
    experiment_summary = experiment.get("summary", {})
    timings = []
    for case, current in experiment_summary.items():
        previous = baseline_summary.get(case)
        if not previous or previous.get("warmMedianMs") is None or current.get("warmMedianMs") is None:
            continue
        previous_ms = previous["warmMedianMs"]
        current_ms = current["warmMedianMs"]
        reduction = ((previous_ms - current_ms) / previous_ms * 100) if previous_ms else None
        timings.append({
            "case": case,
            "baselineWarmMedianMs": previous_ms,
            "currentWarmMedianMs": current_ms,
            "baselineCompleted": previous.get("completed"),
            "currentCompleted": current.get("completed"),
            "reductionPercent": round(reduction, 2) if reduction is not None else None,
        })

    ocr_value_changes = [
        {
            "case": row.get("case"),
            "phase": row.get("phase"),
            "differences": row["baselineOcrDifferences"],
        }
        for row in experiment.get("cases", [])
        if row.get("baselineOcrDifferences")
    ]
    return {"timings": timings, "ocrValueChanges": ocr_value_changes}
