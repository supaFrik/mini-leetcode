<#
.SYNOPSIS
    Nâng một tài khoản hiện có lên quyền ADMIN (hoặc INSTRUCTOR).

.DESCRIPTION
    Script này dùng cho môi trường local: tài khoản admin đầu tiên được tạo bởi
    AdminBootstrapRunner khi khởi động lần đầu. Nếu bạn đã đăng ký tài khoản
    bình thường trước đó và muốn dùng nó làm admin, script này sẽ nâng role
    trực tiếp trong database.

    Script CHỈ chạy được khi API server KHÔNG đang chạy, vì nó sửa trực tiếp
    bảng `users`.

.PARAMETER Username
    Tên đăng nhập cần nâng role.

.PARAMETER Role
    Role đích: ADMIN (mặc định) hoặc INSTRUCTOR.

.PARAMETER DbUrl
    JDBC URL của MySQL. Mặc định lấy từ biến môi trường DB_URL, nếu không có
    thì dùng giá trị cho local.

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

$ErrorActionPreference = 'Stop'

if (-not $DbUrl) { $DbUrl = 'jdbc:mysql://localhost:3307/mini_leetcode?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' }
if (-not $DbUser) { $DbUser = 'leetcode' }
if (-not $DbPassword) { $DbPassword = 'leetcode_password' }

# Chạy lệnh `mysql` với các tham số an toàn (không nhúng mật khẩu vào câu lệnh).
$mysqlArgs = @(
    "--host=$(([System.Uri]$DbUrl -replace '^jdbc:mysql://([^/:?]+).*$','$1'))",
    "--port=3306",
    "--user=$DbUser",
    "--password=$DbPassword",
    '--batch',
    '--skip-column-names'
)

Write-Host "Tra cuoi bang `mysql` client de thay doi role cua '$Username' thanh '$Role'..." -ForegroundColor Cyan

$sql = "UPDATE users SET role = '$Role' WHERE username = '$($Username.Replace("'", "''"))'; SELECT username, role FROM users WHERE username = '$($Username.Replace("'", "''"))';"

try {
    $result = & mysql @mysqlArgs --execute=$sql 2>&1
} catch {
    Write-Host "Khong chay duoc lenh mysql: $_" -ForegroundColor Red
    Write-Host "Hay chay lenh sau thu cong tren may co MySQL client:" -ForegroundColor Yellow
    Write-Host "  UPDATE users SET role = '$Role' WHERE username = '$Username';" -ForegroundColor Yellow
    exit 1
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "Lenh mysql that bai (exit code $LASTEXITCODE)." -ForegroundColor Red
    Write-Host $result -ForegroundColor DarkGray
    exit $LASTEXITCODE
}

if (-not $result) {
    Write-Host "Khong tim thay nguoi dung '$Username'. Hay kiem tra lai ten dang nhap." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Da nang quyen thanh cong:" -ForegroundColor Green
$result | ForEach-Object { Write-Host "  $_" }
