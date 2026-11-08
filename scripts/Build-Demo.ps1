param([switch]$SkipTests)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
Set-DemoEnvironment $s
$npm = Resolve-DemoTool 'npm.cmd'
$maven = Resolve-DemoTool 'mvn.cmd'
$static = Join-Path $script:DemoRoot 'backend\src\main\resources\static'
try {
    Push-Location (Join-Path $script:DemoRoot 'frontend')
    try {
        if (Test-Path -LiteralPath 'package-lock.json') { Invoke-DemoCommand $npm @('ci','--no-audit','--no-fund') }
        else { Invoke-DemoCommand $npm @('install','--no-audit','--no-fund') }
        Invoke-DemoCommand $npm @('run','build')
        if (-not (Test-Path -LiteralPath 'dist\index.html')) { throw 'Frontend build did not create dist/index.html.' }
        if (Test-Path -LiteralPath $static) { Remove-Item -LiteralPath $static -Recurse -Force }
        New-Item -ItemType Directory -Path $static -Force | Out-Null
        Copy-Item -Path 'dist\*' -Destination $static -Recurse -Force
    } finally { Pop-Location }
    Push-Location (Join-Path $script:DemoRoot 'backend')
    try {
        $buildArgs = @('-B','clean','package')
        if ($SkipTests) { $buildArgs += '-DskipTests' }
        Invoke-DemoCommand $maven $buildArgs
    } finally { Pop-Location }
    $artifact = Join-Path $script:DemoRoot 'backend\target\quickbi-demo.jar'
    if (-not (Test-Path -LiteralPath $artifact)) { throw 'Backend build did not create the expected JAR.' }
    Write-Host "Build completed: $artifact" -ForegroundColor Green
    Write-Host 'Next: .\scripts\Start-Demo.ps1'
} catch { throw }
