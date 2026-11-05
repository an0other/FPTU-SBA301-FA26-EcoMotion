param(
 [ValidateSet("staged","legacy")][string]$PipelineMode = "staged",
 [ValidateSet("model","bounded")][string]$OrientationMode = "model",
 [Parameter(Mandatory=$true)][string]$Front,
 [Parameter(Mandatory=$true)][string]$Back,
 [string]$DocumentType = "AUTO",
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$Output = "runs/document-read-01",
 [string]$CornerWeights = "",
 [string]$FieldProfile = "configs/gplx-fields.pilot.json",
 [ValidateSet("paddle","vietocr-seq2seq","vietocr-transformer")][string]$Recognizer = "vietocr-transformer",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [string]$RecognizerDevice = "cpu",
 [switch]$NoQr,
 [ValidateSet("standard","balanced","optimized")][string]$PerformanceMode = "balanced",
 [int]$VietocrCpuThreads = 4,
 [ValidateRange(1,32)][int]$BatchSize = 1,
 [ValidateSet("png","raw")][string]$CropTransport = "png",
 [ValidateSet("full","fields")][string]$RecognitionScope = "full",
 [ValidateSet("full","adaptive")][string]$QrSearch = "full",
 [string]$LocalizerDevice = "cpu",
 [switch]$AllowLegacyPose,
 [switch]$SaveEvidence
)
$arguments = @("tools/read_document.py", "--front", $Front, "--back", $Back, "--document-type", $DocumentType, "--output", $Output)
if ($CornerWeights) { $arguments += @("--corner-weights", $CornerWeights) }
$arguments += @("--localizer-device", $LocalizerDevice)
if ($AllowLegacyPose) { $arguments += "--allow-legacy-pose" }
$arguments += @("--performance-mode", $PerformanceMode, "--vietocr-cpu-threads", $VietocrCpuThreads)
$arguments += @("--recognizer", $Recognizer, "--recognizer-device", $RecognizerDevice)
if ($VietocrPython) { $arguments += @("--vietocr-python", $VietocrPython) }
$arguments += @("--batch-size", $BatchSize, "--crop-transport", $CropTransport, "--recognition-scope", $RecognitionScope, "--qr-search", $QrSearch)
if ($NoQr) { $arguments += "--no-qr" }
if ($FieldProfile) { $arguments += @("--field-profile", $FieldProfile) }
if ($SaveEvidence) { $arguments += "--save-evidence" }
$env:DOCUMENT_AI_PIPELINE_MODE = $PipelineMode
$env:DOCUMENT_AI_ORIENTATION_MODE = $OrientationMode
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Reader error; inspect response.json if available" }
