# Tạo file để import vào Render, không in credentials và không chạy nội dung .env.
# Không ghi đè render.env đang có để tránh mất cấu hình người dùng đã chỉnh.
param([string]$PublicUrl = 'https://tank-battle-web.onrender.com')
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source = Join-Path $workspace '.env'
$destination = Join-Path $workspace 'render.env'
if (Test-Path -LiteralPath $destination) { throw 'render.env đã có; chỉnh file hiện tại, không ghi đè.' }
if (-not (Test-Path -LiteralPath $source)) { throw 'Thiếu .env local; dùng render.env.example để điền thủ công.' }
$publicUri = [Uri]$PublicUrl
if ($publicUri.Scheme -ne 'https' -or $publicUri.AbsolutePath -ne '/' -or $publicUri.Query -or $publicUri.Fragment) {
    throw 'PublicUrl phải là origin HTTPS, không có path/query/fragment.'
}
$values = @{}
foreach ($line in [IO.File]::ReadAllLines($source)) {
    $text = $line.Trim()
    if (-not $text -or $text.StartsWith('#')) { continue }
    $separator = $text.IndexOf('=')
    if ($separator -lt 1) { throw 'Có dòng .env không đúng KEY=VALUE.' }
    $name = $text.Substring(0, $separator).Trim()
    if ($name -notmatch '^[A-Z][A-Z0-9_]*$') { throw 'Tên biến trong .env không hợp lệ.' }
    $values[$name] = $text.Substring($separator + 1).Trim()
}
foreach ($required in @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'BOOTSTRAP_ADMIN_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace($values[$required])) { throw "Thiếu biến $required trong .env." }
}
$jdbcUrl = $values['SPRING_DATASOURCE_URL'].Replace('-pooler.', '.')
if ($jdbcUrl -notmatch '^jdbc:postgresql://[a-zA-Z0-9.-]+\.neon\.tech(:[0-9]+)?/neondb\?') {
    throw 'Cần JDBC Neon URL tách riêng username/password, không nhúng credentials trong URL.'
}
# Dùng direct cho cả Hikari/Flyway để JDBC search_path không phụ thuộc transaction pooler.
if ($jdbcUrl -match '([?&])currentSchema=[^&]*') {
    $jdbcUrl = $jdbcUrl -replace '([?&])currentSchema=[^&]*', '${1}currentSchema=tank_battle'
} else {
    $jdbcUrl += '&currentSchema=tank_battle'
}
$directUrl = $values['SPRING_FLYWAY_URL']
if ([string]::IsNullOrWhiteSpace($directUrl)) { $directUrl = $jdbcUrl }
$directUrl = $directUrl.Replace('-pooler.', '.')
if ($directUrl -notmatch '^jdbc:postgresql://[a-zA-Z0-9.-]+\.neon\.tech(:[0-9]+)?/neondb\?') {
    throw 'Flyway cần direct URL tới database neondb.'
}
if ($directUrl -match '([?&])currentSchema=[^&]*') {
    $directUrl = $directUrl -replace '([?&])currentSchema=[^&]*', '${1}currentSchema=tank_battle'
} else {
    $directUrl += '&currentSchema=tank_battle'
}
$output = @(
    '# Cấu hình demo được phép commit theo yêu cầu chủ đồ án; Docker vẫn loại file này.',
    '# Sửa AUTH_ISSUER thành URL HTTPS thực tế của dịch vụ Render trước khi deploy.',
    'SPRING_PROFILES_ACTIVE=prod',
    'PORT=10000',
    ('SPRING_DATASOURCE_URL=' + $jdbcUrl),
    ('SPRING_DATASOURCE_USERNAME=' + $values['SPRING_DATASOURCE_USERNAME']),
    ('SPRING_DATASOURCE_PASSWORD=' + $values['SPRING_DATASOURCE_PASSWORD']),
    ('SPRING_FLYWAY_URL=' + $directUrl),
    ('AUTH_ISSUER=' + $PublicUrl.TrimEnd('/')),
    'JWT_PRIVATE_KEY=file:/etc/secrets/jwt-private.pem',
    'JWT_PUBLIC_KEY=file:/etc/secrets/jwt-public.pem',
    'JWT_KEY_ID=tank-v1',
    'DB_POOL_SIZE=5',
    'API_DOCS_ENABLED=true',
    'LOCAL_DEMO_ENABLED=false',
    'BOOTSTRAP_ADMIN_ENABLED=true',
    ('BOOTSTRAP_ADMIN_USERNAME=' + $(if ($values['BOOTSTRAP_ADMIN_USERNAME']) { $values['BOOTSTRAP_ADMIN_USERNAME'] } else { 'admin' })),
    ('BOOTSTRAP_ADMIN_PASSWORD=' + $values['BOOTSTRAP_ADMIN_PASSWORD'])
)
[IO.File]::WriteAllLines($destination, $output, [Text.UTF8Encoding]::new($false))
Write-Output 'RENDER_ENV_CREATED: đã tạo render.env; không in giá trị secret.'
