param([ValidateSet('backend','frontend','videos')][string]$Part='frontend')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $repo
switch ($Part) {
 'backend' {
  $entry = Get-Content -LiteralPath (Join-Path $repo '.env') | Where-Object { $_ -match '^POSTGRES_PASSWORD=' }
  if ($entry) { $env:DB_PASSWORD = ($entry -split '=',2)[1].Trim('"',"'") } else { $env:DB_PASSWORD = 'ecomotion' }
  $env:UPLOAD_DIR = Join-Path $repo 'upload'
  Set-Location -LiteralPath (Join-Path $repo 'Backend/EcoMotion')
  & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--app.captcha.enabled=false --spring.jpa.hibernate.ddl-auto=update'
 }
 'frontend' { Set-Location -LiteralPath (Join-Path $repo 'Frontend/EcoMotion'); & npm.cmd run dev -- --host 127.0.0.1 }
 'videos' { & python -m http.server 5174 --bind 127.0.0.1 --directory $PSScriptRoot }
}
