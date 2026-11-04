<#
.SYNOPSIS
    Nạp lại testcase cho bài Two Sum qua API (CHỈ DÙNG CHO DEV).

.DESCRIPTION
    Testcase được lưu trên MinIO nên không nạp bằng SQL được; script này dùng API có sẵn
    POST /api/v1/problems/{id}/test-cases. Chạy lại không bị trùng: xoá testcase cũ của bài rồi nạp lại.
    File lấy từ backend\tools\two-sum\cases (sinh bằng gen.py). File *-hidden.* là testcase ẩn.
    Cần tài khoản INSTRUCTOR hoặc ADMIN. Chạy seed-problems.sql trước để có bài và đề.

.EXAMPLE
    .\backend\tools\seed-two-sum-testcases.ps1 -Username dev_instructor -Password 'dev-password-123'
#>
param(
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    # Bài khác dùng cùng script: -Slug coin-change (file ở backend\tools\<slug>\cases, sinh bằng problems\dsa.py).
    [string]$Slug = "two-sum",
    [string]$BaseUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Stop"
$cases = Join-Path $PSScriptRoot "$Slug\cases"

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail = $Username; password = $Password } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.accessToken)" }

$problem = Invoke-RestMethod -Uri "$BaseUrl/api/v1/problems/$Slug" -Headers $headers
if ($problem.data) { $problem = $problem.data }
$base = "$BaseUrl/api/v1/problems/$($problem.id)/test-cases"

# Bài PUBLISHED bị khoá testcase, nên đi đúng vòng đời: PUBLISHED -> ARCHIVED -> DRAFT, nạp xong thì REVIEWING -> PUBLISHED.
function Move-Status([string]$status) {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/problems/$($problem.id)/transitions" -Headers $headers `
        -ContentType "application/json" -Body (@{ status = $status } | ConvertTo-Json) | Out-Null
}
if ($problem.status -eq "PUBLISHED") { Move-Status "ARCHIVED"; Move-Status "DRAFT" }
elseif ($problem.status -eq "ARCHIVED") { Move-Status "DRAFT" }
elseif ($problem.status -eq "REVIEWING") { Move-Status "DRAFT" }

$old = Invoke-RestMethod -Uri $base -Headers $headers  # mảng thuần, không bọc trong ApiResponse
foreach ($tc in @($old)) {
    if ($tc.id) { Invoke-RestMethod -Method Delete -Uri "$base/$($tc.id)" -Headers $headers | Out-Null }
}

$order = 0
foreach ($in in Get-ChildItem $cases -Filter *.in | Sort-Object Name) {
    $out = [IO.Path]::ChangeExtension($in.FullName, ".out")
    $hidden = if ($in.BaseName -like "*-hidden") { "true" } else { "false" }
    # curl.exe vì Invoke-RestMethod của PowerShell 5.1 không gửi được multipart.
    & curl.exe -sS -f -X POST "$base`?isHidden=$hidden&orderIndex=$order" -H "Authorization: Bearer $($login.accessToken)" `
        -F "inputFile=@$($in.FullName)" -F "outputFile=@$out" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Upload failed: $($in.Name)" }
    Write-Host "uploaded $($in.BaseName) (hidden=$hidden)"
    $order++
}
Move-Status "REVIEWING"
Move-Status "PUBLISHED"
Write-Host "$Slug is PUBLISHED with $order test cases"
