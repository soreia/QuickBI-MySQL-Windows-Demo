# Development mode; keep this terminal open.
. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
Set-DemoEnvironment $s
$maven = Resolve-DemoTool 'mvn.cmd'
Assert-DemoPortFree ([int]$s.ApiPort)
Push-Location (Join-Path $script:DemoRoot 'backend')
try { Invoke-DemoCommand $maven @('spring-boot:run') } finally { Pop-Location }
