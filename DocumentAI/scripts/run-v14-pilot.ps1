param(
 [string]$TrainPython = ".\.venv-corners\Scripts\python.exe",
 [string]$ReaderPython = ".\.venv\Scripts\python.exe",
 [string]$Dataset = "training/dataset-demo-01",
 [Parameter(Mandatory=$true)][string]$Front,
 [Parameter(Mandatory=$true)][string]$Back,
 [string]$DocumentType = "VN_DRIVING_LICENSE",
 [string]$Output = "runs/v14-pilot-01",
 [string]$Device = "0",
 [string]$ReaderDevice = "cpu",
 [int]$Epochs = 60
)
$ErrorActionPreference = "Stop"
function Invoke-PythonChecked {
 param([string]$Executable, [string[]]$Arguments)
 & $Executable @Arguments
 if ($LASTEXITCODE -ne 0) { throw "Step failed with exit code $LASTEXITCODE" }
}
if (Test-Path $Output) { throw "Output exists; choose a new folder: $Output" }
if (!(Test-Path $Front) -or !(Test-Path $Back)) { throw "Test image missing" }
# Import checks before the long training step.
Invoke-PythonChecked $TrainPython @("-c", "import ultralytics, cv2, yaml, torch; print('Training imports OK; CUDA:', torch.cuda.is_available())")
Invoke-PythonChecked $ReaderPython @("-c", "import ultralytics, paddleocr, cv2; print('Reader imports OK')")
New-Item -ItemType Directory -Path $Output | Out-Null
$segDataset = Join-Path $Output "dataset-seg"
$trainOutput = Join-Path $Output "training"
Invoke-PythonChecked $TrainPython @("tools/build_segmentation_dataset.py", "--source", $Dataset, "--output", $segDataset)
Invoke-PythonChecked $TrainPython @("tools/train_segmentation.py", "--data", (Join-Path $segDataset "dataset.yaml"), "--output", $trainOutput, "--epochs", "$Epochs", "--batch", "2", "--device", $Device)
$weights = Join-Path $trainOutput "weights/best.pt"
Invoke-PythonChecked $TrainPython @("tools/evaluate_localizer.py", "--dataset", $segDataset, "--weights", $weights, "--device", $Device, "--split", "val", "--output", (Join-Path $Output "evaluation-val"))
Invoke-PythonChecked $ReaderPython @("tools/read_document.py", "--front", $Front, "--back", $Back, "--document-type", $DocumentType, "--corner-weights", $weights, "--localizer-device", $ReaderDevice, "--output", (Join-Path $Output "reader"), "--save-evidence")
Write-Host "Finished. Inspect evaluation-val/evaluation.json and reader/response.json under $Output"
