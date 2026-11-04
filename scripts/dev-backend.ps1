$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Get-Content -LiteralPath (Join-Path $projectRoot '.env') | ForEach-Object {
 if ($_ -match '^([A-Z_]+)=(.+)$') { [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process') }
}
Set-Location (Join-Path $projectRoot 'backend')
$env:SERVER_ADDRESS='127.0.0.1'
mvn spring-boot:run
