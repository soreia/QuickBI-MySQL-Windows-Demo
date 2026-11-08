. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
Write-Host '=== Windows native demo environment ===' -ForegroundColor Cyan
$java = Resolve-DemoTool 'java.exe'
$javac = Resolve-DemoTool 'javac.exe'
$maven = Resolve-DemoTool 'mvn.cmd'
$node = Resolve-DemoTool 'node.exe'
$npm = Resolve-DemoTool 'npm.cmd'
$mysql = Resolve-MySql $s
$javaText = & $java --version
if ($LASTEXITCODE -ne 0) { throw 'Unable to run java --version.' }
Write-Host ($javaText -join "`n")
$javacText = & $javac -version
if ($LASTEXITCODE -ne 0 -or ($javacText -join '') -notmatch 'javac (\d+)') { throw 'A full JDK 21+ is required, not only a JRE.' }
if ([int]$Matches[1] -lt 21) { throw 'JDK 21+ is required.' }
Invoke-DemoCommand $maven @('-version')
$nodeText = & $node --version
if ($nodeText -notmatch '^v(\d+)\.(\d+)\.') { throw 'Cannot detect Node version.' }
if ([int]$Matches[1] -lt 22 -or ([int]$Matches[1] -eq 22 -and [int]$Matches[2] -lt 12)) { throw 'Node 22.12+ is required. Node 22.x is the recommended demo baseline.' }
Write-Host "Node: $nodeText"
Invoke-DemoCommand $npm @('--version')
Invoke-DemoCommand $mysql @('--version')
$client = New-Object System.Net.Sockets.TcpClient
try {
    $pending = $client.BeginConnect($s.DbHost, [int]$s.DbPort, $null, $null)
    if (-not $pending.AsyncWaitHandle.WaitOne(2000)) { throw 'MySQL port timed out.' }
    $client.EndConnect($pending)
    Write-Host "MySQL TCP endpoint reachable: $($s.DbHost):$($s.DbPort)" -ForegroundColor Green
} finally { $client.Dispose() }
Write-Host 'PASS: tool and TCP checks. This does not prove database credentials, SQL migration or full application startup.' -ForegroundColor Green
