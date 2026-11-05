param([switch]$WithOcr)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
function Run-Python {
    & $script:Python @args
    if ($LASTEXITCODE -ne 0) { throw "Python command failed" }
}
if (-not (Test-Path ".venv/Scripts/python.exe")) {
    py -3.11 -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw "Install Python 3.11 or create .venv with Python 3.12 first" }
}
$script:Python = (Resolve-Path ".venv/Scripts/python.exe").Path
Run-Python -m pip install -e ".[dev,qr]"
if ($WithOcr) {
    Run-Python -m pip install "paddlepaddle==3.3.1" "paddleocr==3.7.0"
}
Run-Python -m pytest -q
