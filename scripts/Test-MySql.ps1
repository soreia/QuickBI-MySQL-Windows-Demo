# Use a separate LOCAL test database; never run IT against the application's database.
. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
$maven = Resolve-DemoTool 'mvn.cmd'
$env:TEST_DB_NAME = $s.Database + 'it'
$env:TEST_DB_URL = "jdbc:mysql://$($s.DbHost):$($s.DbPort)/$($env:TEST_DB_NAME)?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
$env:TEST_DB_USER = $s.DbUser + 'it'
$env:TEST_DB_PASSWORD = $s.DbPassword
$env:TEST_MIGRATION_USER = $s.MigrationUser + 'it'
$env:TEST_MIGRATION_PASSWORD = $s.MigrationPassword
Write-Host "Using isolated local test database: $($env:TEST_DB_NAME)" -ForegroundColor Cyan
Write-Host 'Run Initialize-Database.ps1 -ForTests once BEFORE this test.'
Push-Location (Join-Path $script:DemoRoot 'backend')
try { Invoke-DemoCommand $maven @('-B','-Pmysql-it','verify') } finally { Pop-Location }
