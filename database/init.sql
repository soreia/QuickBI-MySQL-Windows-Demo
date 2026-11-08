-- Manual fallback, execute as local MySQL administrator. Fixed public LOCAL demo credentials only.
-- Prefer scripts/Initialize-Database.ps1; it reads local-settings.json and also supports a separate IT database.
CREATE DATABASE IF NOT EXISTS quickbiwin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE USER IF NOT EXISTS 'qbwinmigrator'@'localhost' IDENTIFIED BY 'Local-Migrator-Demo-2026';
GRANT ALL PRIVILEGES ON quickbiwin.* TO 'qbwinmigrator'@'localhost' WITH GRANT OPTION;
CREATE USER IF NOT EXISTS 'qbwinportal'@'localhost' IDENTIFIED BY 'Local-Portal-Demo-2026';
GRANT SELECT ON quickbiwin.* TO 'qbwinportal'@'localhost';
CREATE USER IF NOT EXISTS 'qbwinmigrator'@'127.0.0.1' IDENTIFIED BY 'Local-Migrator-Demo-2026';
GRANT ALL PRIVILEGES ON quickbiwin.* TO 'qbwinmigrator'@'127.0.0.1' WITH GRANT OPTION;
CREATE USER IF NOT EXISTS 'qbwinportal'@'127.0.0.1' IDENTIFIED BY 'Local-Portal-Demo-2026';
GRANT SELECT ON quickbiwin.* TO 'qbwinportal'@'127.0.0.1';
