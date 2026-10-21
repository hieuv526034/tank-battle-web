# Smoke test prod profile với PostgreSQL portable TEST. Chỉ đọc file env fixture, không .env gốc/Neon.
# tools/test-postgres.ps1 -ProductionSmoke gọi script này sau khi integration test đạt.
param([string]$JarPath = (Join-Path $PSScriptRoot '../build/libs/tank-battle-web-1.0.0.jar'))
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
# Dùng java.exe thật của JDK thay vì launcher shim trong javapath. Nếu dừng shim trên
# Windows, JVM con có thể còn sống và giữ file JAR, khiến lần build sau không rename được.
$javaHomePath = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHomePath)) {
    $runtimeSettings = & java '-XshowSettings:properties' '-version' 2>&1
    $javaHomeEntry = $runtimeSettings | Where-Object { $_ -match '^\s*java.home\s*=' } | Select-Object -First 1
    if ($null -eq $javaHomeEntry) { throw 'Không xác định được java.home của JDK.' }
    $javaHomePath = ([string]$javaHomeEntry -replace '^\s*java.home\s*=\s*', '').Trim()
}
$javaExecutable = Join-Path $javaHomePath 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'Không tìm thấy java.exe thật trong JDK.' }
$testUrl = $env:TEST_DATABASE_URL
if ($testUrl -notmatch '^jdbc:postgresql://127\.0\.0\.1:([0-9]+)/postgres$') {
    throw 'Smoke test chỉ nhận cluster PostgreSQL test loopback của test-postgres.ps1.'
}
$pgPort = $Matches[1]
$driverDirectory = Join-Path $workspace 'build/tools/jdbc'
$drivers = @(Get-ChildItem -LiteralPath $driverDirectory -Filter 'postgresql-*.jar' -ErrorAction SilentlyContinue)
if ($drivers.Count -ne 1) { throw 'Cần chạy Gradle copyPostgresDriver trước smoke test.' }
$driver = $drivers[0].FullName
$databaseTool = Join-Path $PSScriptRoot 'SmokeTestDatabase.java'
$jar = [IO.Path]::GetFullPath($JarPath)
if (-not (Test-Path -LiteralPath $jar)) { throw 'Cần chạy gradlew.bat build để có JAR trước smoke test.' }
$runRoot = [IO.Path]::GetFullPath((Join-Path $workspace ('build/production-smoke-' + [Guid]::NewGuid().ToString('N'))))
$reportRoot = Join-Path $workspace 'build/verification'
New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
$variables = @('APP_ENV_FILE', 'SPRING_PROFILES_ACTIVE', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD', 'SPRING_FLYWAY_URL', 'AUTH_ISSUER', 'JWT_PRIVATE_KEY',
    'JWT_PUBLIC_KEY', 'API_DOCS_ENABLED', 'PORT', 'BOOTSTRAP_ADMIN_PASSWORD', 'BOOTSTRAP_ADMIN_ENABLED', 'BOOTSTRAP_ADMIN_USERNAME')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$app = $null
try {
    # DB mới chứng minh Flyway tự tạo bảng khi khởi động thật, không dựa vào DB của JUnit vừa chạy.
    & java --class-path $driver $databaseTool create $testUrl
    if ($LASTEXITCODE -ne 0) { throw 'Không tạo được DB smoke trong cluster test.' }
    & java (Join-Path $PSScriptRoot 'GenerateJwtKeys.java') $runRoot
    if ($LASTEXITCODE -ne 0) { throw 'Không sinh được JWT key dùng một lần cho test.' }

    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $httpPort = $listener.LocalEndpoint.Port
    $listener.Stop()
    $env:SPRING_PROFILES_ACTIVE = 'prod'
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$pgPort/tankbattle_smoke"
    $env:SPRING_FLYWAY_URL = $env:SPRING_DATASOURCE_URL
    $env:SPRING_DATASOURCE_USERNAME = 'postgres'
    $env:SPRING_DATASOURCE_PASSWORD = 'ci_test_only'
    $env:AUTH_ISSUER = 'https://smoke.example.invalid'
    $env:JWT_PRIVATE_KEY = 'file:' + (Join-Path $runRoot 'jwt-private.pem').Replace('\', '/')
    $env:JWT_PUBLIC_KEY = 'file:' + (Join-Path $runRoot 'jwt-public.pem').Replace('\', '/')
    $env:API_DOCS_ENABLED = 'false'
    $env:BOOTSTRAP_ADMIN_PASSWORD = 'BootstrapSmoke!2026'
    $env:BOOTSTRAP_ADMIN_ENABLED = 'true'
    $env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
    $env:PORT = [string]$httpPort
    # Kiểm luồng giống Run trong IDE: prod đọc .env tại working directory mặc định.
    # Không truyền APP_ENV_FILE/DB/JWT: tránh bỏ sót lỗi tìm file mà test đường dẫn tường minh che mất.
    # File nằm trong runRoot riêng và luôn được dọn; không đọc hoặc sao chép .env production.
    $fixtureEnvironment = @{}
    $fixtureLines = @()
    foreach ($name in $variables | Where-Object { $_ -ne 'APP_ENV_FILE' }) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        $fixtureEnvironment[$name] = $value
        $fixtureLines += $name + '=' + $value
    }
    $fixtureFile = Join-Path $runRoot '.env'
    [IO.File]::WriteAllLines($fixtureFile, [string[]]$fixtureLines, [Text.UTF8Encoding]::new($false))
    foreach ($name in $variables | Where-Object { $_ -ne 'SPRING_PROFILES_ACTIVE' }) {
        # Xóa hẳn entry; một số tổ hợp PowerShell/.NET giữ $null thành chuỗi rỗng,
        # khiến env rỗng có ưu tiên cao hơn giá trị đúng trong file fixture.
        Remove-Item -LiteralPath ('Env:' + $name) -ErrorAction SilentlyContinue
    }
    $app = Start-Process -FilePath $javaExecutable -WindowStyle Hidden -PassThru `
        -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $runRoot `
        -RedirectStandardOutput (Join-Path $reportRoot 'production-startup.log') `
        -RedirectStandardError (Join-Path $reportRoot 'production-startup-error.log')

    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline -and -not $app.HasExited) {
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$httpPort/actuator/health/readiness" -TimeoutSec 3
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Prod profile chưa sẵn sàng; xem build/verification/production-startup.log.' }
    $response = Invoke-WebRequest "http://127.0.0.1:$httpPort/api/auth/me" -Headers @{'Accept-Language'='en'} -SkipHttpErrorCheck
    $body = $response.Content | ConvertFrom-Json
    if ($response.StatusCode -ne 401 -or $body.code -ne 'AUTH_001' -or $body.locale -ne 'en') {
        throw 'Auth của prod profile không trả đúng lỗi/locale khi chưa bật Google.'
    }
    # Health/JPA có thể đạt dù JDBC tìm sai schema. Đăng nhập và gọi API thật để kiểm cả hai.
    $csrf = Invoke-RestMethod "http://127.0.0.1:$httpPort/api/auth/csrf" -SessionVariable smokeSession
    $loginPayload = @{
        username = $fixtureEnvironment['BOOTSTRAP_ADMIN_USERNAME']
        password = $fixtureEnvironment['BOOTSTRAP_ADMIN_PASSWORD']
    } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod "http://127.0.0.1:$httpPort/api/auth/login" -Method Post `
        -WebSession $smokeSession -ContentType 'application/json' -Body $loginPayload `
        -Headers @{'X-XSRF-TOKEN' = $csrf.data.token}
    if ([string]::IsNullOrWhiteSpace($login.data.accessToken)) { throw 'Admin bootstrap chưa đăng nhập được.' }
    $authorization = @{Authorization = 'Bearer ' + $login.data.accessToken}
    $admin = Invoke-RestMethod "http://127.0.0.1:$httpPort/api/admin/me" -Headers $authorization
    if ($admin.data.role -ne 'ADMIN' -or $admin.data.username -ne 'admin') {
        throw 'Tài khoản bootstrap chưa có đúng quyền quản trị.'
    }
    $rooms = Invoke-RestMethod "http://127.0.0.1:$httpPort/api/rooms" -Headers $authorization
    if ($null -eq $rooms.data -or @($rooms.data).Count -ne 0) { throw 'DB smoke mới phải có danh sách phòng trống.' }
    & java --class-path $driver $databaseTool verify $fixtureEnvironment['SPRING_DATASOURCE_URL']
    if ($LASTEXITCODE -ne 0) { throw 'Thiếu migration trong DB smoke.' }
    Write-Output 'PRODUCTION_PROFILE_STARTUP_OK: default .env from working directory (no APP_ENV_FILE), health UP, auth 401/en, admin login/API, rooms API, Flyway V1-V6.'
} finally {
    if ($null -ne $app -and -not $app.HasExited) {
        # Chỉ dừng Java do smoke test này tạo; không dừng app/IDE của người phát triển.
        Stop-Process -Id $app.Id
        $app.WaitForExit(10000) | Out-Null
    }
    foreach ($name in $variables) {
        if ($null -eq $previous[$name]) {
            Remove-Item -LiteralPath ('Env:' + $name) -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
    }
    $allowedPrefix = [IO.Path]::GetFullPath((Join-Path $workspace 'build')) + [IO.Path]::DirectorySeparatorChar
    if (-not $runRoot.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($runRoot) -notmatch '^production-smoke-[a-f0-9]{32}$') { throw 'Từ chối dọn đường dẫn ngoài smoke test.' }
    if (Test-Path -LiteralPath $runRoot) { Remove-Item -LiteralPath $runRoot -Recurse -Force }
}
