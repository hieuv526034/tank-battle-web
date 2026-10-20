# Kiểm tra production chỉ đọc; script này KHÔNG khởi ứng dụng/Flyway.
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $PSScriptRoot 'Import-LocalEnvironment.ps1')
$drivers = @(Get-ChildItem -LiteralPath (Join-Path $workspace 'build/tools/jdbc') -Filter 'postgresql-*.jar' -ErrorAction SilentlyContinue)
if ($drivers.Count -ne 1) { throw 'Cần chạy gradlew.bat copyPostgresDriver trước để chuẩn bị JDBC driver.' }
$driver = $drivers[0].FullName
& java --class-path $driver (Join-Path $PSScriptRoot 'CheckDatabase.java')
if ($LASTEXITCODE -ne 0) { throw 'Kiểm tra DB thất bại; xem SQLSTATE, không log thông tin truy cập.' }
