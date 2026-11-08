# Shared helpers. Compatible with Windows PowerShell 5.1 and PowerShell 7.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:DemoRoot = Split-Path -Parent $PSScriptRoot

function Get-DemoSettings {
    $file = Join-Path $script:DemoRoot 'local-settings.json'
    if (-not (Test-Path -LiteralPath $file)) {
        Copy-Item -LiteralPath (Join-Path $script:DemoRoot 'local-settings.example.json') -Destination $file
        Write-Host 'Created local-settings.json from the local demo template.'
    }
    $settings = Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($name in @('DbHost','DbPort','Database','DbUser','DbPassword','MigrationUser','MigrationPassword','MySqlExe','ApiPort','FrontendPort','SessionGeneration','HmacKey')) {
        if ($null -eq $settings.PSObject.Properties[$name]) { throw "Missing settings property: $name" }
    }
    if ($settings.DbHost -notin @('127.0.0.1','localhost')) { throw 'This demo supports a LOCAL MySQL instance only.' }
    foreach ($name in @('Database','DbUser','MigrationUser')) {
        if ([string]$settings.$name -notmatch '^[a-z][a-z0-9]{1,27}$') { throw "$name must contain 2-28 lowercase letters/digits, starting with a letter." }
    }
    if ($settings.Database -in @('mysql','sys','information_schema','performance_schema')) { throw 'System databases are not allowed.' }
    if ($settings.DbUser -eq $settings.MigrationUser) { throw 'Runtime and migration users must be different.' }
    foreach ($name in @('DbPassword','MigrationPassword')) {
        if ([string]$settings.$name -notmatch '^[A-Za-z0-9.-]{12,128}$') { throw "$name must be a 12-128 character LOCAL demo password (letters, digits, dots or hyphens)." }
    }
    foreach ($name in @('DbPort','ApiPort','FrontendPort')) {
        if ([string]$settings.$name -notmatch '^\d+$' -or [int]$settings.$name -lt 1024 -or [int]$settings.$name -gt 65535) { throw "$name must be a port between 1024 and 65535." }
    }
    if ($settings.ApiPort -eq $settings.FrontendPort -or $settings.DbPort -eq $settings.ApiPort -or $settings.DbPort -eq $settings.FrontendPort) { throw 'Database, API and frontend ports must be distinct.' }
    if ([string]::IsNullOrWhiteSpace($settings.HmacKey) -or $settings.HmacKey.Length -lt 32) { throw 'HmacKey must have at least 32 characters.' }
    if ([string]::IsNullOrWhiteSpace($settings.SessionGeneration)) { throw 'SessionGeneration cannot be empty.' }
    return $settings
}

function Resolve-DemoTool {
    param([Parameter(Mandatory=$true)][string]$Name)
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $cmd) { throw "Required tool not found: $Name. Install it, add its bin directory to PATH, and reopen PowerShell." }
    return $cmd.Source
}

function Resolve-MySql {
    param($Settings)
    if (-not [string]::IsNullOrWhiteSpace($Settings.MySqlExe)) {
        if (-not (Test-Path -LiteralPath $Settings.MySqlExe -PathType Leaf)) { throw 'MySqlExe does not point to mysql.exe.' }
        return $Settings.MySqlExe
    }
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($null -ne $cmd) { return $cmd.Source }
    $candidate = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.4\bin\mysql.exe'
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    throw 'mysql.exe was not found. Add MySQL bin to PATH, or set MySqlExe in local-settings.json.'
}

function Invoke-DemoCommand {
    param([string]$File, [string[]]$CommandArgs = @())
    & $File @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw "Command failed (exit $LASTEXITCODE): $File" }
}

function Set-DemoEnvironment {
    param($Settings)
    # Explicitly replace inherited DB_URL/PORT values so an old shell cannot point at a different database.
    $env:DB_NAME = $Settings.Database
    $env:DB_URL = "jdbc:mysql://$($Settings.DbHost):$($Settings.DbPort)/$($Settings.Database)?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    $env:DB_USER = $Settings.DbUser
    $env:DB_PASSWORD = $Settings.DbPassword
    $env:MIGRATION_USER = $Settings.MigrationUser
    $env:MIGRATION_PASSWORD = $Settings.MigrationPassword
    $env:PORT = [string]$Settings.ApiPort
    $env:BIND_ADDRESS = '127.0.0.1'
    $env:DEMO_MODE = 'mock'
    $env:DEMO_HMAC_KEY = $Settings.HmacKey
    $env:SESSION_GENERATION = $Settings.SessionGeneration
    $env:DEMO_ORIGINS = "http://localhost:$($Settings.ApiPort),http://127.0.0.1:$($Settings.ApiPort),http://localhost:$($Settings.FrontendPort),http://127.0.0.1:$($Settings.FrontendPort)"
    $env:VITE_API_TARGET = "http://127.0.0.1:$($Settings.ApiPort)"
    $env:VITE_DEV_PORT = [string]$Settings.FrontendPort
}

function Assert-DemoPortFree {
    param([int]$Port)
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) { throw "Port $Port is already in use. Stop the old demo or choose another port in local-settings.json." }
}
