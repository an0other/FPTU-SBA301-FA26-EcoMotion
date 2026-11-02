param(
 [Parameter(Mandatory=$true)][string]$Front,
 [Parameter(Mandatory=$true)][string]$Back,
 [Parameter(Mandatory=$true)][string]$CornerWeights,
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [string]$FieldProfile = "",
 [string]$DocumentType = "VN_DRIVING_LICENSE",
 [string]$Output = "runs/v16-ocr-only-01",
 [string]$LocalizerDevice = "cpu",
 [string]$RecognizerDevice = "cpu",
 [switch]$WithQr
)
$ErrorActionPreference = "Stop"
$arguments = @("tools/compare_full_readers.py", "--front", $Front, "--back", $Back,
 "--python", $Python, "--vietocr-python", $VietocrPython, "--corner-weights", $CornerWeights,
 "--document-type", $DocumentType, "--output", $Output,
 "--localizer-device", $LocalizerDevice, "--recognizer-device", $RecognizerDevice)
if ($FieldProfile) { $arguments += @("--field-profile", $FieldProfile) }
if ($WithQr) { $arguments += "--with-qr" }
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "A reader failed. Inspect comparison.json and per-engine logs." }
