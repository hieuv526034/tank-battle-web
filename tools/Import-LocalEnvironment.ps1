# Nạp .env vào process hiện tại, không in giá trị hoặc chạy nội dung file như PowerShell.
param([string]$Path = (Join-Path $PSScriptRoot '..\.env'))
$ErrorActionPreference = 'Stop'
foreach ($line in [IO.File]::ReadAllLines([IO.Path]::GetFullPath($Path))) {
    $text = $line.Trim()
    if (-not $text -or $text.StartsWith('#')) { continue }
    $separator = $text.IndexOf('=')
    if ($separator -lt 1) { throw 'Dòng .env không đúng KEY=VALUE' }
    $name = $text.Substring(0, $separator).Trim()
    if ($name -notmatch '^[A-Z][A-Z0-9_]*$') { throw 'Tên biến môi trường không hợp lệ' }
    $value = $text.Substring($separator + 1).Trim()
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
