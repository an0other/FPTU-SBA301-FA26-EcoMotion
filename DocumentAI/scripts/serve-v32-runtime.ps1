param(
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [ValidateSet("cpu","cuda:0")][string]$RecognizerDevice = "cpu",
 [ValidateSet("fixed","device")][string]$Experiment = "fixed",
 [string]$CornerWeights = "models/localizer/document-card-seg-v1.pt",
 [string]$WarmupFront = "inputs/gplx-test-front.JPEG",
 [string]$WarmupBack = "inputs/gplx-test-back.JPEG",
 [ValidateSet("development","production")][string]$Environment = "development",
 [int]$Port = 8001
)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
. (Join-Path $PSScriptRoot "import-env.ps1")
$env:DOCUMENT_AI_ENVIRONMENT = $Environment
if ($Experiment -eq "fp16" -and $RecognizerDevice -eq "cpu") { throw "FP16 requires explicit CUDA. Select fixed/device/bucket for CPU." }
$rawSettings = & $Python tools/experiment_settings_v32.py --name $Experiment
if ($LASTEXITCODE -ne 0) { throw "Failed to load experiment settings" }
$experimentSettings = $rawSettings | ConvertFrom-Json
foreach ($entry in $experimentSettings.PSObject.Properties) { [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, "Process") }
$env:DOCUMENT_AI_READER_OPTIMIZATION = "efficient"
$env:DOCUMENT_AI_ORIENTATION_OCR = "shared"
$env:DOCUMENT_AI_ADDRESS_RETRY = "0"
$env:DOCUMENT_AI_WARMUP_DOCUMENT_TYPE = "VN_DRIVING_LICENSE"
$env:DOCUMENT_AI_PREVIEW_MAX_SIDE = "1280"
$env:DOCUMENT_AI_DETECTION_MAX_SIDE = "1280"
$settings = @{
 Python=$Python; VietocrPython=$VietocrPython; RecognizerDevice=$RecognizerDevice;
 CornerWeights=$CornerWeights; WarmupFront=$WarmupFront; WarmupBack=$WarmupBack;
 Port=$Port; PipelineMode="staged"; OrientationMode="model";
 PerformanceMode="optimized"; BatchSize=[int]$experimentSettings.DOCUMENT_AI_BATCH_SIZE;
 CropTransport="raw"; RecognitionScope="fields"; QrSearch="adaptive"; LocalizerDevice="cpu"
}
& (Join-Path $PSScriptRoot "serve-reader.ps1") @settings
