$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
function New-LocalSecret {
 $bytes = New-Object byte[] 24
 [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
 return [Convert]::ToBase64String($bytes)
}
$names = @('DB_PASSWORD','OPERATOR_PASSWORD','QUALITY_PASSWORD','READER_PASSWORD','SUPERVISOR_PASSWORD')
$existing = if (Test-Path -LiteralPath $envFile) { Get-Content -LiteralPath $envFile } else { @() }
# Conserva las claves existentes y solo agrega las que falten (p. ej. SUPERVISOR_PASSWORD en V2).
$missing = $names | Where-Object { $n = $_; -not ($existing | Where-Object { $_ -match "^$n=" }) }
if (-not $missing) { Write-Host '.env ya existe; se conservan sus claves.'; exit 0 }
$lines = @($existing) + ($missing | ForEach-Object { "$_=$(New-LocalSecret)" })
[IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
Write-Host "Claves agregadas en .env: $($missing -join ', '). Consúltelas localmente; nunca las suba a GitHub."
