# Development mode; run in a SECOND terminal while Start-Backend.ps1 is running.
. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
Set-DemoEnvironment $s
$npm = Resolve-DemoTool 'npm.cmd'
Assert-DemoPortFree ([int]$s.FrontendPort)
Push-Location (Join-Path $script:DemoRoot 'frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules')) {
        if (Test-Path -LiteralPath 'package-lock.json') { Invoke-DemoCommand $npm @('ci','--no-audit','--no-fund') }
        else { Invoke-DemoCommand $npm @('install','--no-audit','--no-fund') }
    }
    Write-Host "Frontend URL: http://localhost:$($s.FrontendPort)" -ForegroundColor Cyan
    Invoke-DemoCommand $npm @('run','dev')
} finally { Pop-Location }
