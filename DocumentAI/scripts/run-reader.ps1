param(
 [Parameter(Mandatory=$true)][string]$Front,
 [Parameter(Mandatory=$true)][string]$Back,
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$Corners = "",
 [switch]$CompareModels,
 [int]$Repeats = 3
)
$ErrorActionPreference = "Stop"
$previousProgress = $env:DOCUMENT_AI_PROGRESS
try {
 $env:DOCUMENT_AI_PROGRESS = "1"
 if ($CompareModels) {
  $arguments = @("tools/benchmark_suite.py", "--profiles", "configs/reader-v6-tiny.example.json", "configs/reader-v6-small.example.json", "--front", $Front, "--back", $Back, "--repeats", "$Repeats")
 } else {
  $arguments = @("-m", "document_ai.cli", "analyze", "--profile", "configs/reader-default.example.json", "--front", $Front, "--back", $Back, "--diagnostic", "--save-ocr-evidence")
 }
 if ($Corners) { $arguments += @("--corners", $Corners) }
 & $Python @arguments
 if ($LASTEXITCODE -ne 0) { throw "Reader command failed" }
} finally { $env:DOCUMENT_AI_PROGRESS = $previousProgress }
