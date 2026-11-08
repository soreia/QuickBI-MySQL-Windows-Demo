. (Join-Path $PSScriptRoot 'Common.ps1')
$javac = Resolve-DemoTool 'javac.exe'
$java = Resolve-DemoTool 'java.exe'
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('quickbi-security-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    Invoke-DemoCommand $javac @('-d',$temp,(Join-Path $script:DemoRoot 'backend\src\main\java\demo\Crypto.java'),(Join-Path $script:DemoRoot 'scripts\SecuritySelfTest.java'))
    Invoke-DemoCommand $java @('-cp',$temp,'SecuritySelfTest')
} finally { Remove-Item -LiteralPath $temp -Recurse -Force }
