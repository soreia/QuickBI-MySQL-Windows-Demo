param([switch]$Development)
. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
$port = $s.ApiPort
if ($Development) { $port = $s.FrontendPort }
$py = Get-Command py.exe -ErrorAction SilentlyContinue
$python = Get-Command python.exe -ErrorAction SilentlyContinue
$scriptFile = Join-Path $script:DemoRoot 'scripts\smoke.py'
if ($null -ne $py) { Invoke-DemoCommand $py.Source @('-3',$scriptFile,"http://localhost:$port") }
elseif ($null -ne $python) { Invoke-DemoCommand $python.Source @($scriptFile,"http://localhost:$port") }
else { throw 'Optional HTTP test requires Python 3.10+ (py.exe or python.exe in PATH).' }
