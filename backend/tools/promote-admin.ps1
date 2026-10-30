<#
.SYNOPSIS
    Nâng một tài khoản hiện có lên quyền ADMIN (hoặc INSTRUCTOR).

.DESCRIPTION
    Script này dùng cho môi trường local: tài khoản admin đầu tiên được tạo bởi
    AdminBootstrapRunner khi khởi động lần đầu. Nếu bạn đã đăng ký tài khoản
    bình thường trước đó và muốn dùng nó làm admin, script này sẽ nâng role
    trực tiếp trong database.

    Role được nhúng trong JWT, nên sau khi nâng quyền người dùng PHẢI đăng nhập
    lại (hoặc refresh token) để token mới mang role mới.

.PARAMETER Username
    Tên đăng nhập cần nâng role.

.PARAMETER Role
    Role đích: ADMIN (mặc định) hoặc INSTRUCTOR.

.PARAMETER DbUrl
    JDBC URL của MySQL. Mặc định lấy từ biến môi trường DB_URL, nếu không có
    thì dùng giá trị cho local (cổng 3307 — cổng host của container).

.PARAMETER DbUser
    Tên đăng nhập database. Mặc định lấy từ DB_USERNAME, nếu không có thì 'leetcode'.

.PARAMETER DbPassword
    Mật khẩu database. Mặc định lấy từ DB_PASSWORD, nếu không có thì 'leetcode_password'.

.EXAMPLE
    .\promote-admin.ps1 -Username adminuser

.EXAMPLE
    .\promote-admin.ps1 -Username teacher01 -Role INSTRUCTOR
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, HelpMessage = "Tên đăng nhập cần nâng role")]
    [string]$Username,

    [ValidateSet('ADMIN', 'INSTRUCTOR')]
    [string]$Role = 'ADMIN',

    [string]$DbUrl = $env:DB_URL,
    [string]$DbUser = $env:DB_USERNAME,
    [string]$DbPassword = $env:DB_PASSWORD
)

$ErrorActionPreference = 'Continue'

if (-not $DbUrl) { $DbUrl = 'jdbc:mysql://localhost:3307/mini_leetcode?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' }
if (-not $DbUser) { $DbUser = 'leetcode' }
if (-not $DbPassword) { $DbPassword = 'leetcode_password' }

# Tách host/port từ JDBC URL: jdbc:mysql://host:port/db?params
if ($DbUrl -match 'jdbc:mysql://([^/:?]+)(?::(\d+))?(?:/([^?]*))?') {
    $dbHost = $Matches[1]
    $dbPort = if ($Matches[2]) { $Matches[2] } else { '3306' }
    $dbName = if ($Matches[3]) { $Matches[3] } else { 'mini_leetcode' }
} else {
    Write-Host "Khong phan tich duoc DbUrl '$DbUrl'." -ForegroundColor Red
    exit 1
}

if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    Write-Host "Khong tim thay lenh 'mysql' client trong PATH." -ForegroundColor Red
    Write-Host "Hay chay lenh sau thu cong tren may co MySQL client:" -ForegroundColor Yellow
    Write-Host "  UPDATE users SET role = '$Role' WHERE username = '$Username';" -ForegroundColor Yellow
    exit 1
}

# Mật khẩu truyền qua biến môi trường MYSQL_PWD: tránh cảnh báo
# "Using a password on the command line interface can be insecure".
$env:MYSQL_PWD = $DbPassword

$mysqlArgs = @(
    "--host=$dbHost",
    "--port=$dbPort",
    "--user=$DbUser",
    "--database=$dbName",
    '--batch',
    '--skip-column-names'
)

$safeName = $Username.Replace("'", "''")
$sql = "UPDATE users SET role = '$Role' WHERE username = '$safeName'; SELECT username, role FROM users WHERE username = '$safeName';"

Write-Host "Tra cuoi bang `mysql` client de thay doi role cua '$Username' thanh '$Role'..." -ForegroundColor Cyan
Write-Host "  $dbHost`:$dbPort" -ForegroundColor DarkGray

try {
    $result = & mysql @mysqlArgs --execute=$sql 2>&1
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "Lenh mysql that bai (exit code $LASTEXITCODE)." -ForegroundColor Red
    $result | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    exit 1
}

$rows = @($result | Where-Object { $_ -and $_ -isnot [System.Management.Automation.ErrorRecord] })
if ($rows.Count -eq 0) {
    Write-Host "Khong tim thay nguoi dung '$Username'. Hay kiem tra lai ten dang nhap." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Da nang quyen thanh cong:" -ForegroundColor Green
$rows | ForEach-Object { Write-Host "  $_" }
Write-Host "Nguoi dung can dang nhap lai de token moi mang role '$Role'." -ForegroundColor Yellow
