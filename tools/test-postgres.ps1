# PostgreSQL 18.6 portable dành riêng TEST Windows. Không đọc .env, không cần Docker/service.
# Tải artifact từ Maven Central qua Gradle lần đầu; mỗi lần tạo cluster chỉ listen 127.0.0.1.
param([switch]$ProductionSmoke, [string]$ProductionJar)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pgRoot = Join-Path $workspace 'build/postgres-test'
$binaryRoot = Join-Path $pgRoot 'server'
$gradle = Join-Path $workspace 'gradlew.bat'
$reportRoot = Join-Path $workspace 'build/verification'
# Binary/cluster/log là đầu ra kiểm thử; giữ dưới build, Gradle dùng cache chuẩn của người dùng.
New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
# Xuất driver vào đường dẫn ổn định; không tìm thủ công trong cache nội bộ Gradle.
& $gradle --no-daemon --console=plain -p $workspace copyPostgresDriver *> (Join-Path $reportRoot 'postgres-tools.log')
if ($LASTEXITCODE -ne 0) { throw 'Không chuẩn bị được JDBC driver: build/verification/postgres-tools.log' }
if (-not (Test-Path -LiteralPath (Join-Path $binaryRoot 'bin/initdb.exe'))) {
    & $gradle --no-daemon --console=plain -p $workspace copyPostgresTestBinary *> (Join-Path $reportRoot 'postgres-binary.log')
    if ($LASTEXITCODE -ne 0) { throw 'Không tải được PostgreSQL test.' }
    $artifact = Join-Path $workspace 'build/tools/postgres/embedded-postgres-binaries-windows-amd64-18.6.0.jar'
    New-Item -ItemType Directory -Path $binaryRoot -Force | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry('postgres-windows-x86_64.txz'), (Join-Path $pgRoot 'postgres.txz'), $true)
    } finally { $archive.Dispose() }
    & tar -xf (Join-Path $pgRoot 'postgres.txz') -C $binaryRoot
    if ($LASTEXITCODE -ne 0) { throw 'Không giải nén được PostgreSQL test.' }
}
$runRoot = [IO.Path]::GetFullPath((Join-Path $pgRoot ('run-' + [Guid]::NewGuid().ToString('N'))))
$data = Join-Path $runRoot 'data'
New-Item -ItemType Directory -Path $runRoot | Out-Null
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$pgCtl = Join-Path $binaryRoot 'bin/pg_ctl.exe'
$started = $false
$previousUrl = $env:TEST_DATABASE_URL
$previousUser = $env:TEST_DATABASE_USERNAME
$previousPassword = $env:TEST_DATABASE_PASSWORD
try {
    & (Join-Path $binaryRoot 'bin/initdb.exe') -D $data -U postgres -A trust -E UTF8 --no-locale *> (Join-Path $runRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'initdb thất bại; xem log trong thư mục run test.' }
    $process = Start-Process -FilePath $pgCtl -WindowStyle Hidden -PassThru -ArgumentList @(
        '-D', ('"' + $data + '"'), '-l', ('"' + (Join-Path $runRoot 'postgres.log') + '"'),
        '-o', ('"-p ' + $port + ' -h 127.0.0.1"'), '-w', 'start')
    # Start-Process -Wait đợi cả cây con postgres đang sống; chỉ đợi tiến trình pg_ctl.
    if (-not $process.WaitForExit(60000) -or $process.ExitCode -ne 0) { throw 'Không khởi động được PostgreSQL test.' }
    $started = $true
    $env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$port/postgres"
    $env:TEST_DATABASE_USERNAME = 'postgres'
    $env:TEST_DATABASE_PASSWORD = ''
    Write-Output 'Đang test PostgreSQL 18.6 cục bộ; không sử dụng Neon production.'
    Push-Location $workspace
    try {
        & $gradle --no-daemon --console=plain postgresTest *> (Join-Path $reportRoot 'postgres-test.log')
        if ($LASTEXITCODE -ne 0) { throw 'Test PostgreSQL thất bại: build/verification/postgres-test.log' }
        Write-Output 'POSTGRES_BUSINESS_TESTS_OK'
        if ($ProductionSmoke) {
            # Cho phép chọn JAR ở build riêng nếu ứng dụng local đang giữ JAR mặc định.
            if ([string]::IsNullOrWhiteSpace($ProductionJar)) {
                & (Join-Path $PSScriptRoot 'test-production-startup.ps1')
            } else {
                & (Join-Path $PSScriptRoot 'test-production-startup.ps1') -JarPath $ProductionJar
            }
        }
    } finally { Pop-Location }
} finally {
    if ($started) {
        $stop = Start-Process -FilePath $pgCtl -WindowStyle Hidden -PassThru -ArgumentList @('-D', ('"' + $data + '"'), '-m', 'fast', '-w', 'stop')
        if (-not $stop.WaitForExit(60000) -or $stop.ExitCode -ne 0) { throw 'Không dừng được PostgreSQL test; giữ thư mục để kiểm tra.' }
    }
    $env:TEST_DATABASE_URL = $previousUrl
    $env:TEST_DATABASE_USERNAME = $previousUser
    $env:TEST_DATABASE_PASSWORD = $previousPassword
    # Xác minh đích trước khi xóa đệ quy. Chỉ xóa cluster GUID do chính lần chạy này tạo.
    $allowedPrefix = [IO.Path]::GetFullPath($pgRoot) + [IO.Path]::DirectorySeparatorChar
    if (-not $runRoot.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($runRoot) -notmatch '^run-[a-f0-9]{32}$') { throw 'Từ chối xóa đường dẫn ngoài test cluster.' }
    Remove-Item -LiteralPath $runRoot -Recurse -Force
}
