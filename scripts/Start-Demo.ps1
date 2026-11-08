# Run in the foreground. Ctrl+C stops only this Java application, not your MySQL service.
. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
Set-DemoEnvironment $s
$java = Resolve-DemoTool 'java.exe'
$jar = Join-Path $script:DemoRoot 'backend\target\quickbi-demo.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'JAR missing. Run .\scripts\Build-Demo.ps1 first.' }
Assert-DemoPortFree ([int]$s.ApiPort)
Write-Host "Starting local portal: http://localhost:$($s.ApiPort)" -ForegroundColor Cyan
Write-Host 'Wait for Spring Boot startup/health UP, then open the URL. Press Ctrl+C to stop.'
Invoke-DemoCommand $java @('-XX:MaxRAMPercentage=65','-jar',$jar)
