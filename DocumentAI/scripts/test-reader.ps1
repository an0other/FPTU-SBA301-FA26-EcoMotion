param(
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$Manifest = "evaluation/local.json",
 [string]$Output = "runs/reader-check-01"
)
$ErrorActionPreference = "Stop"
$previous = $env:DOCUMENT_AI_PROGRESS
try {
 $env:DOCUMENT_AI_PROGRESS = "1"
 & $Python tools/evaluate_batch.py --manifest $Manifest --output "$Output/evaluation"
 if ($LASTEXITCODE -ne 0) { throw "Evaluation contains case errors; inspect evaluation.json" }
 & $Python tools/diagnose_batch.py --manifest $Manifest --evaluation "$Output/evaluation/evaluation.json" --output "$Output/diagnostics" --retry-number
 if ($LASTEXITCODE -ne 0) { throw "Diagnostics contains errors; inspect diagnostics.json" }
} finally { $env:DOCUMENT_AI_PROGRESS = $previous }
