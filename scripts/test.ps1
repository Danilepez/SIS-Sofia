$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Get-Content -LiteralPath (Join-Path $projectRoot '.env') | ForEach-Object {
 if ($_ -match '^([A-Z_]+)=(.+)$') { [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process') }
}
$env:DB_URL='jdbc:postgresql://localhost:5433/sofia_test'
Set-Location (Join-Path $projectRoot 'backend')
mvn -B verify
exit $LASTEXITCODE
