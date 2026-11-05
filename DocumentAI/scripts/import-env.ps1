$ErrorActionPreference = "Stop"
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
 throw "Missing $envFile. Add DOCUMENT_AI_API_KEY to the local .env file."
}

foreach ($line in Get-Content -LiteralPath $envFile) {
 $entry = $line.Trim()
 if (-not $entry -or $entry.StartsWith("#")) { continue }
 if ($entry -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { continue }

 $name = $matches[1]
 $value = $matches[2].Trim()
 if ($value.Length -ge 2) {
  $first = $value.Substring(0, 1)
  $last = $value.Substring($value.Length - 1, 1)
  if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
   $value = $value.Substring(1, $value.Length - 2)
  }
 }

 $existingValue = [Environment]::GetEnvironmentVariable($name, "Process")
 if ($name -in @("DOCUMENT_AI_API_KEY", "DOCUMENT_AI_PIPELINE_TRACE") -or [string]::IsNullOrWhiteSpace($existingValue)) {
  [Environment]::SetEnvironmentVariable($name, $value, "Process")
 }
}

if (-not $env:DOCUMENT_AI_API_KEY -or $env:DOCUMENT_AI_API_KEY.Length -lt 32) {
 throw "DOCUMENT_AI_API_KEY is missing or shorter than 32 characters in $envFile."
}
if ($env:DOCUMENT_AI_API_KEY -notmatch '^[\x21-\x7E]+$') {
 throw "DOCUMENT_AI_API_KEY must contain ASCII characters only."
}

$loadedFrom = [Environment]::GetEnvironmentVariable("DOCUMENT_AI_ENV_LOADED_FROM", "Process")
if ($loadedFrom -ne $envFile) {
 [Environment]::SetEnvironmentVariable("DOCUMENT_AI_ENV_LOADED_FROM", $envFile, "Process")
 Write-Host "Loaded DOCUMENT_AI_API_KEY from $envFile (length: $($env:DOCUMENT_AI_API_KEY.Length))."
}
