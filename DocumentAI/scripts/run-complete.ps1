param(
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$Manifest = "evaluation/aligned.local.json",
 [string]$Output = "runs/complete-reader-01",
 [string]$ReuseEvaluation = "",
 [switch]$SaveEvidence
)
$ErrorActionPreference = "Stop"
$arguments = @("tools/run_complete.py", "--manifest", $Manifest, "--output", $Output)
if ($ReuseEvaluation) { $arguments += @("--reuse-evaluation", $ReuseEvaluation) }
if ($SaveEvidence) { $arguments += "--save-evidence" }
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Reader run contains errors. Inspect complete-report.json if present." }
