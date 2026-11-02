param(
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$Manifest = "evaluation/aligned.local.json",
 [string]$Evaluation = "runs/reader-check-aligned-01/evaluation/evaluation.json",
 [string]$Output = "runs/recheck-aligned-01",
 [switch]$RetryNumber
)
$ErrorActionPreference = "Stop"
if (Test-Path $Output) { throw "Output already exists; choose a new directory" }
& $Python tools/refresh_evaluation.py --manifest $Manifest --evaluation $Evaluation --output "$Output/evaluation"
if ($LASTEXITCODE -ne 0) { throw "Report refresh failed; inspect evaluation output" }
$arguments = @("tools/diagnose_batch.py", "--manifest", $Manifest, "--evaluation", $Evaluation, "--output", "$Output/diagnostics")
if ($RetryNumber) { $arguments += "--retry-number" }
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Diagnostic errors; inspect diagnostics.json" }
