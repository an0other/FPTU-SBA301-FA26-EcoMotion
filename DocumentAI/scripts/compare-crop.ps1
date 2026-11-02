param(
 [Parameter(Mandatory=$true)][string]$Crop,
 [string]$ExpectedFile = "",
 [string]$Expected = "",
 [string]$Python = ".\.venv\Scripts\python.exe",
 [string]$VietocrPython = "",
 [int]$Repeats = 3
)
$ErrorActionPreference = "Stop"
$arguments = @("tools/compare_crop.py", "--image", $Crop, "--paddle-python", $Python, "--repeats", "$Repeats", "--engines", "paddle-v5-latin", "paddle-v6-tiny", "paddle-v6-small")
if ($VietocrPython) { $arguments += @("vietocr-transformer", "vietocr-seq2seq", "--vietocr-python", $VietocrPython) }
if ($Expected) { $arguments += @("--expected", $Expected) }
if ($ExpectedFile) { $arguments += @("--expected-file", $ExpectedFile) }
& $Python @arguments
if ($LASTEXITCODE -ne 0) { throw "Crop comparison failed" }
