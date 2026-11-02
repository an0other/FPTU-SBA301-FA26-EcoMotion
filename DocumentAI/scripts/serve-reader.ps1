param(
 [ValidateSet("staged","legacy")][string]$PipelineMode = "staged",
 [ValidateSet("model","bounded")][string]$OrientationMode = "model",
 [Parameter(Mandatory=$true)][string]$CornerWeights,
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [string]$FieldProfile = "configs/gplx-fields.pilot.json",
 [string]$Recognizer = "vietocr-transformer",
 [string]$LocalizerDevice = "cpu",
 [string]$RecognizerDevice = "cpu",
 [int]$Port = 8001,
 [string]$WarmupFront = "",
 [string]$WarmupBack = "",
 [ValidateSet("standard","balanced","optimized")][string]$PerformanceMode = "balanced",
 [int]$VietocrCpuThreads = 4,
 [ValidateRange(1,32)][int]$BatchSize = 1,
 [ValidateSet("png","raw")][string]$CropTransport = "png",
 [ValidateSet("full","fields")][string]$RecognitionScope = "full",
 [ValidateSet("full","adaptive")][string]$QrSearch = "full",
 [switch]$NoQr
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "import-env.ps1")
if ([bool]$WarmupFront -ne [bool]$WarmupBack) { throw "Supply both WarmupFront and WarmupBack" }
if (-not $env:DOCUMENT_AI_ENVIRONMENT) { $env:DOCUMENT_AI_ENVIRONMENT = "production" }
if ($WarmupFront) {
 $env:DOCUMENT_AI_WARMUP_FRONT = (Resolve-Path $WarmupFront).Path
 $env:DOCUMENT_AI_WARMUP_BACK = (Resolve-Path $WarmupBack).Path
} else {
 Remove-Item Env:DOCUMENT_AI_WARMUP_FRONT -ErrorAction SilentlyContinue
 Remove-Item Env:DOCUMENT_AI_WARMUP_BACK -ErrorAction SilentlyContinue
}
$env:DOCUMENT_AI_PIPELINE_MODE = $PipelineMode
$env:DOCUMENT_AI_ORIENTATION_MODE = $OrientationMode
$env:DOCUMENT_AI_CORNER_WEIGHTS = (Resolve-Path $CornerWeights).Path
$env:DOCUMENT_AI_VIETOCR_PYTHON = (Resolve-Path $VietocrPython).Path
$env:DOCUMENT_AI_FIELD_PROFILE = (Resolve-Path $FieldProfile).Path
$env:DOCUMENT_AI_BATCH_SIZE = [string]$BatchSize
$env:DOCUMENT_AI_CROP_TRANSPORT = $CropTransport
$env:DOCUMENT_AI_RECOGNITION_SCOPE = $RecognitionScope
$env:DOCUMENT_AI_QR_SEARCH = $QrSearch
$env:DOCUMENT_AI_PERFORMANCE_MODE = $PerformanceMode
$env:DOCUMENT_AI_VIETOCR_CPU_THREADS = [string]$VietocrCpuThreads
$env:DOCUMENT_AI_RECOGNIZER = $Recognizer
$env:DOCUMENT_AI_LOCALIZER_DEVICE = $LocalizerDevice
$env:DOCUMENT_AI_RECOGNIZER_DEVICE = $RecognizerDevice
$env:DOCUMENT_AI_QR_ENABLED = if ($NoQr) { "0" } else { "1" }
$logFolder = New-Item -ItemType Directory -Force -Path "runs/api-reader"
$env:DOCUMENT_AI_VIETOCR_LOG = Join-Path $logFolder.FullName "vietocr-worker.log"
& $Python -m uvicorn document_ai.reader.api:create_app --factory --host 127.0.0.1 --port $Port --workers 1
if ($LASTEXITCODE -ne 0) { throw "Reader server exited with an error" }
