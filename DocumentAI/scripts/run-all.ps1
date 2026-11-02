param(
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$Front = "inputs/front-nolight.jpg",
 [string]$Back = "inputs/back-nolight.jpg",
 [string]$Corners = "configs/corners.local.json",
 [int]$Repeats = 3,
 [switch]$CompareModels
)
$ErrorActionPreference = "Stop"
& $Python -m pytest -q
if ($LASTEXITCODE -ne 0) { throw "Tests failed" }
if (-not (Test-Path $Corners)) {
 & $Python tools/select_corners.py --image $Front --side FRONT --output $Corners
 if ($LASTEXITCODE -ne 0) { throw "Front corner selection failed" }
 & $Python tools/select_corners.py --image $Back --side BACK --output $Corners
 if ($LASTEXITCODE -ne 0) { throw "Back corner selection failed" }
}
if (-not (Test-Path $Corners)) { throw "Save the corner selections first" }
if ($CompareModels) {
 & $Python tools/benchmark_suite.py --profiles configs/reader-v5.example.json configs/reader-v6-tiny.example.json configs/reader-v6-small.example.json --front $Front --back $Back --corners $Corners --repeats $Repeats
} else {
 & $Python -m document_ai.cli analyze --profile configs/reader-v5.example.json --front $Front --back $Back --corners $Corners --diagnostic --save-ocr-evidence
}
if ($LASTEXITCODE -ne 0) { throw "Reader command failed" }
