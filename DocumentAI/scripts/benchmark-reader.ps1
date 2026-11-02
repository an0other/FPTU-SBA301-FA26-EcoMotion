param(
 [ValidateSet("staged","legacy")][string]$PipelineMode = "staged",
 [ValidateSet("model","bounded")][string]$OrientationMode = "model",
 [Parameter(Mandatory=$true)][string]$Front,
 [Parameter(Mandatory=$true)][string]$Back,
 [Parameter(Mandatory=$true)][string]$CornerWeights,
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [string]$FieldProfile = "configs/gplx-fields.pilot.json",
 [string]$DocumentType = "VN_DRIVING_LICENSE",
 [ValidateSet("standard","balanced","optimized")][string]$PerformanceMode = "balanced",
 [int]$VietocrCpuThreads = 4,
 [ValidateRange(1,32)][int]$BatchSize = 1,
 [ValidateSet("png","raw")][string]$CropTransport = "png",
 [ValidateSet("full","fields")][string]$RecognitionScope = "full",
 [ValidateSet("full","adaptive")][string]$QrSearch = "full",
 [ValidateSet("cpu","cuda:0")][string]$RecognizerDevice = "cpu",
 [int]$Repeats = 3,
 [string]$ReferenceResponse = "",
 [string]$Output = "runs/v18-latency-01",
 [switch]$NoQr
)
$arguments = @("tools/benchmark_reader_latency.py", "--front", $Front, "--back", $Back,
 "--corner-weights", $CornerWeights, "--vietocr-python", $VietocrPython,
 "--profile", $FieldProfile, "--document-type", $DocumentType,
 "--performance-mode", $PerformanceMode, "--recognizer-device", $RecognizerDevice, "--cpu-threads", $VietocrCpuThreads,
 "--repeats", $Repeats, "--output", $Output)
$arguments += @("--batch-size", $BatchSize, "--crop-transport", $CropTransport, "--recognition-scope", $RecognitionScope, "--qr-search", $QrSearch)
if ($NoQr) { $arguments += "--no-qr" }
if ($ReferenceResponse) { $arguments += @("--reference-response", $ReferenceResponse) }
$env:DOCUMENT_AI_PIPELINE_MODE = $PipelineMode
$env:DOCUMENT_AI_ORIENTATION_MODE = $OrientationMode
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Benchmark failed; inspect benchmark.json and vietocr-worker.log" }
