param(
 [string]$CpuPython = ".\.venv-vietocr\Scripts\python.exe",
 [string]$GpuEnv = ".venv-vietocr-gpu"
)
$ErrorActionPreference = "Stop"
if (Test-Path $GpuEnv) { throw "GPU environment already exists. Reuse it or choose another GpuEnv; this script does not overwrite it." }
$CpuPython = (Resolve-Path $CpuPython).Path
$requirements = & $CpuPython -m pip freeze
if ($LASTEXITCODE -ne 0) { throw "Cannot inspect working VietOCR environment" }
$folder = New-Item -ItemType Directory -Force -Path "runs/gpu-setup"
$requirementsPath = Join-Path $folder.FullName "vietocr-base-without-torch.txt"
$requirements | Where-Object { $_ -notmatch '^(torch|torchvision|torchaudio)(==| @)' } | Set-Content -Encoding UTF8 $requirementsPath
& py -3.11 -m venv $GpuEnv
if ($LASTEXITCODE -ne 0) { throw "Cannot create Python 3.11 GPU environment" }
$GpuPython = Join-Path $GpuEnv "Scripts/python.exe"
& $GpuPython -m pip install --upgrade pip
if ($LASTEXITCODE -ne 0) { throw "pip setup failed" }
& $GpuPython -m pip install torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cu124
if ($LASTEXITCODE -ne 0) { throw "CUDA PyTorch installation failed" }
& $GpuPython -m pip install -r $requirementsPath
if ($LASTEXITCODE -ne 0) { throw "VietOCR dependencies installation failed" }
& $GpuPython -m pip check
if ($LASTEXITCODE -ne 0) { throw "GPU environment has dependency conflicts" }
& $GpuPython tools/check_vietocr_runtime.py --device cuda:0
if ($LASTEXITCODE -ne 0) { throw "GPU runtime unavailable; inspect output above. CPU environment is unchanged." }
