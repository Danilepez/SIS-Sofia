$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) { Write-Host '.env ya existe; se conservan sus claves.'; exit 0 }
function New-LocalSecret {
 $bytes = New-Object byte[] 24
 [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
 return [Convert]::ToBase64String($bytes)
}
$lines = @('DB_PASSWORD','OPERATOR_PASSWORD','QUALITY_PASSWORD','READER_PASSWORD') | ForEach-Object { "$_=$(New-LocalSecret)" }
[IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
Write-Host 'Credenciales generadas en .env. Consúltelas localmente; nunca las suba a GitHub.'
