param(
 [Parameter(Mandatory=$true)][string]$Front,
 [Parameter(Mandatory=$true)][string]$Back,
 [Parameter(Mandatory=$true)][string]$CornerWeights,
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = ".\.venv-vietocr\Scripts\python.exe",
 [string]$GpuVietocrPython = "",
 [string]$FieldProfile = "configs/gplx-fields.pilot.json",
 [string]$ReferenceResponse = "",
 [ValidateRange(2,20)][int]$Repeats = 3,
 [ValidateRange(1,32)][int]$VietocrCpuThreads = 4,
 [string]$Output = "runs/v22-optimization-matrix-01"
)
$ErrorActionPreference = "Stop"
$arguments = @("tools/test_optimizations.py", "--front", $Front, "--back", $Back,
 "--corner-weights", $CornerWeights, "--vietocr-python", $VietocrPython,
 "--profile", $FieldProfile, "--repeats", $Repeats, "--cpu-threads", $VietocrCpuThreads,
 "--output", $Output)
if ($GpuVietocrPython) { $arguments += @("--gpu-python", $GpuVietocrPython) }
if ($ReferenceResponse) { $arguments += @("--reference-response", $ReferenceResponse) }
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Some cases failed. Inspect matrix.json and case logs; completed cases are retained." }
