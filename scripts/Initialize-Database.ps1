param([switch]$ForTests, [string]$AdminUser = 'root')
. (Join-Path $PSScriptRoot 'Common.ps1')
$s = Get-DemoSettings
$mysql = Resolve-MySql $s
if ($AdminUser -notmatch '^[A-Za-z0-9.-]{1,32}$') { throw 'Invalid admin user name.' }
$db = $s.Database
$runtimeUser = $s.DbUser
$migrationUser = $s.MigrationUser
if ($ForTests) { $db += 'it'; $runtimeUser += 'it'; $migrationUser += 'it' }
$sql = "CREATE DATABASE IF NOT EXISTS ``$db`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;"
foreach ($accountHost in @('localhost','127.0.0.1')) {
    $sql += "CREATE USER IF NOT EXISTS '$migrationUser'@'$accountHost' IDENTIFIED BY '$($s.MigrationPassword)';"
    $sql += "GRANT ALL PRIVILEGES ON ``$db``.* TO '$migrationUser'@'$accountHost' WITH GRANT OPTION;"
    $sql += "CREATE USER IF NOT EXISTS '$runtimeUser'@'$accountHost' IDENTIFIED BY '$($s.DbPassword)';"
    $sql += "GRANT SELECT ON ``$db``.* TO '$runtimeUser'@'$accountHost';"
}
Write-Host "Initializing LOCAL database: $db. Existing databases/data are NOT dropped." -ForegroundColor Cyan
Write-Host 'MySQL will prompt for the administrator password; it is not saved by this script.'
# Only public LOCAL demo account passwords appear in the SQL. Never put a real production credential in this demo config.
Invoke-DemoCommand $mysql @('--protocol=TCP', "--host=$($s.DbHost)", "--port=$($s.DbPort)", "--user=$AdminUser", '--password', '--default-character-set=utf8mb4', "--execute=$sql")
Write-Host 'Database/account initialization completed. Flyway will create tables and seed data on first startup.' -ForegroundColor Green
Write-Host 'CREATE USER IF NOT EXISTS does not change an existing password. Use a new dedicated demo name if old credentials conflict.'
