param(
 [ValidateSet("staged","legacy")][string]$PipelineMode = "staged",
 [ValidateSet("model","bounded")][string]$OrientationMode = "model",
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [string]$Manifest = "evaluation/local.json",
 [Parameter(Mandatory=$true)][string]$CornerWeights,
 [string]$FieldProfile = "configs/gplx-fields.pilot.json",
 [ValidateSet("cpu","cuda:0")][string]$RecognizerDevice = "cpu",
 [ValidateRange(1,32)][int]$VietocrCpuThreads = 4,
 [string]$Output = "runs/v23-reader-evaluation-01",
 [switch]$SaveEvidence,
 [switch]$IncludeNoQr
)
$ErrorActionPreference = "Stop"
$arguments = @("tools/evaluate_reader_configs.py", "--manifest", $Manifest, "--output", $Output,
 "--corner-weights", $CornerWeights, "--vietocr-python", $VietocrPython,
 "--profile", $FieldProfile, "--device", $RecognizerDevice, "--cpu-threads", $VietocrCpuThreads)
if ($SaveEvidence) { $arguments += "--save-evidence" }
if ($IncludeNoQr) { $arguments += "--include-no-qr" }
$env:DOCUMENT_AI_PIPELINE_MODE = $PipelineMode
$env:DOCUMENT_AI_ORIENTATION_MODE = $OrientationMode
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Evaluation failed or had processing errors. Inspect evaluation.json and worker logs." }
