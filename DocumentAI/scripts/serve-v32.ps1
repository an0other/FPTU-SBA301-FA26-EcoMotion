param(
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [ValidateSet("cpu","cuda:0")][string]$RecognizerDevice = "cpu",
 [ValidateSet("fixed","device")][string]$Experiment = "device",
 [ValidateSet("off","diagnostic")][string]$ImageAnalysis = "diagnostic",
 [string]$CornerWeights = "models/localizer/document-card-seg-v1.pt",
 [string]$WarmupFront = "inputs/gplx-test-front.JPEG",
 [string]$WarmupBack = "inputs/gplx-test-back.JPEG",
 [ValidateSet("development","production")][string]$Environment = "development",
 [int]$Port = 8001
)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:DOCUMENT_AI_IMAGE_ANALYSIS = $ImageAnalysis
& (Join-Path $PSScriptRoot "serve-v32-runtime.ps1") -Python $Python -VietocrPython $VietocrPython `
 -RecognizerDevice $RecognizerDevice -Experiment $Experiment -CornerWeights $CornerWeights `
 -WarmupFront $WarmupFront -WarmupBack $WarmupBack -Environment $Environment -Port $Port
