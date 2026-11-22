<#
.SYNOPSIS
    One-command local setup (DEV ONLY): build, start the Docker stack, load seed and demo data.

.DESCRIPTION
    Steps: check requirements -> create .env from .env.example -> mvnw clean package -> docker compose up -d --build
    -> wait for the API -> load problems, Mini Bank project, contests, grading rules, test cases and demo data.
    Safe to run again: every SQL file upserts or deletes its own previous rows, and test cases are only uploaded
    for problems whose test case count on the server differs from backend\tools\<slug>\cases.
    SQL files are copied into the MySQL container (docker cp) and run there, never piped.

.EXAMPLE
    .\setup-demo.ps1
.EXAMPLE
    # Skip the Maven build and image rebuild (stack already built), only reload data.
    .\setup-demo.ps1 -SkipBuild
#>
param([switch]$SkipBuild)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Tools = Join-Path $Root "backend\tools"
$MysqlContainer = "mini_leetcode_mysql"
$ApiContainer = "mini_leetcode_api"
$ApiUrl = "http://localhost:8080"

function Step([string]$msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }
function Fail([string]$msg) { Write-Host ""; Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }
function Test-Command([string]$name) { [bool](Get-Command $name -ErrorAction SilentlyContinue) }

# ---------------------------------------------------------------- 1. Requirements
Step "Checking requirements"
if (-not (Test-Command docker)) { Fail "Docker is not installed. Install Docker Desktop: https://www.docker.com/products/docker-desktop/" }
cmd /c "docker info >nul 2>&1"
if ($LASTEXITCODE -ne 0) { Fail "Docker is installed but not running. Start Docker Desktop, wait until it is ready, then run this script again." }
cmd /c "docker compose version >nul 2>&1"
if ($LASTEXITCODE -ne 0) { Fail "'docker compose' (Compose v2) is not available. Update Docker Desktop." }

if (-not (Test-Command java)) { Fail "Java is not installed. Install JDK 17 or newer (e.g. https://adoptium.net/) and reopen PowerShell." }
$javaVersion = (cmd /c "java -version 2>&1" | Out-String)
if ($javaVersion -notmatch 'version "(\d+)') { Fail "Could not read the Java version from 'java -version':`n$javaVersion" }
if ([int]$Matches[1] -lt 17) { Fail "Java 17 or newer is required, found: $(($javaVersion -split "`n")[0].Trim()). Install JDK 17+ and put it first on PATH (or set JAVA_HOME)." }

# Python only generates SQL from the files in backend\tools\problems (standard library, no packages).
$Python = $null
foreach ($candidate in @(@("python"), @("py", "-3"))) {
    if (-not (Test-Command $candidate[0])) { continue }
    $args1 = @($candidate | Select-Object -Skip 1) + @("-c", "import sys; sys.exit(0 if sys.version_info >= (3, 8) else 1)")
    & $candidate[0] @args1 *> $null
    if ($LASTEXITCODE -eq 0) { $Python = $candidate; break }
}
if (-not $Python) { Fail "Python 3.8+ is required to generate the seed SQL. Install it from https://www.python.org/downloads/ (tick 'Add python.exe to PATH') and reopen PowerShell." }
if (-not (Test-Command curl.exe)) { Fail "curl.exe was not found (it ships with Windows 10 1803+)." }
Write-Host "Docker, Java $($Matches[1]) and Python found."

function Invoke-Python([string[]]$scriptArgs) {
    $all = @($Python | Select-Object -Skip 1) + $scriptArgs
    & $Python[0] @all
    if ($LASTEXITCODE -ne 0) { Fail "python $($scriptArgs -join ' ') failed." }
}

# ---------------------------------------------------------------- 2. .env
Step "Preparing .env"
$envFile = Join-Path $Root ".env"
if (-not (Test-Path $envFile)) {
    Copy-Item (Join-Path $Root ".env.example") $envFile
    Write-Host "Created .env from .env.example."
} else {
    Write-Host ".env already exists, keeping it."
}
$envVars = @{}
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') { $envVars[$Matches[1]] = $Matches[2].Trim('"', "'") }
}
$AdminUser = $envVars["BOOTSTRAP_ADMIN_USERNAME"]
$AdminPassword = $envVars["BOOTSTRAP_ADMIN_PASSWORD"]
if (-not $AdminUser -or -not $AdminPassword) { Fail "BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD must be set in .env (see .env.example)." }
$FrontendPort = if ($envVars["FRONTEND_PORT"]) { $envVars["FRONTEND_PORT"] } else { "3100" }

# ---------------------------------------------------------------- 3. Build and start
Push-Location $Root
try {
    if (-not $SkipBuild) {
        Step "Building backend (mvnw clean package -DskipTests)"
        Push-Location (Join-Path $Root "backend")
        try {
            & .\mvnw.cmd -q clean package -DskipTests
            if ($LASTEXITCODE -ne 0) { Fail "Maven build failed (see the output above)." }
        } finally { Pop-Location }

        Step "Starting Docker stack (docker compose up -d --build)"
        & docker compose up -d --build
    } else {
        Step "Starting Docker stack (docker compose up -d)"
        & docker compose up -d
    }
    if ($LASTEXITCODE -ne 0) { Fail "docker compose up failed (see the output above). Ports 3306, 6379, 8080, 9000, 9001 and $FrontendPort must be free." }

    # The judge runs submissions in these images; pull them now so the first submission is not slow.
    foreach ($image in @("eclipse-temurin:17-jdk", "python:3.12-slim")) {
        & docker pull -q $image | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "Could not pull $image (a judge sandbox image). Check your internet connection." }
    }
    & docker tag eclipse-temurin:17-jdk mini-leetcode-judge-runner:java17
} finally { Pop-Location }

Step "Waiting for the API to become healthy"
$deadline = (Get-Date).AddMinutes(5)
do {
    $health = (& docker inspect -f "{{.State.Health.Status}}" $ApiContainer 2>$null)
    if ($health -eq "healthy") { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($health -ne "healthy") { Fail "The API did not become healthy within 5 minutes. Check: docker logs $ApiContainer" }
Write-Host "API is healthy."

# Detect host port mapping if overridden in docker-compose.override.yml (e.g. 8088:8080)
$portMapping = (& docker port $ApiContainer 8080 2>$null | Select-Object -First 1)
if ($portMapping -match ':(\d+)$') {
    $ApiUrl = "http://localhost:$($Matches[1])"
}

# ---------------------------------------------------------------- 4. Data
function Import-Sql([string]$path) {
    $name = Split-Path $path -Leaf
    & docker cp $path "${MysqlContainer}:/tmp/$name"
    if ($LASTEXITCODE -ne 0) { Fail "docker cp $name failed." }
    # MYSQL_PWD instead of -p: no password warning on stderr.
    & docker exec -e MYSQL_PWD=leetcode_password $MysqlContainer sh -c "mysql --default-character-set=utf8mb4 -u leetcode mini_leetcode < /tmp/$name > /dev/null"
    if ($LASTEXITCODE -ne 0) { Fail "Loading $name into MySQL failed." }
    & docker exec $MysqlContainer rm -f "/tmp/$name"
    Write-Host "loaded $name"
}

Step "Loading problems (seed-problems.sql)"
Invoke-Python @((Join-Path $Tools "problems\seed_sql.py"))
Import-Sql (Join-Path $Tools "seed-problems.sql")

Step "Loading Mini Bank project, lessons and study plan (seed-mini-bank-project.sql)"
Invoke-Python @((Join-Path $Tools "problems\mini_bank_project.py"), "sql")
Import-Sql (Join-Path $Tools "seed-mini-bank-project.sql")

Step "Loading contests (seed-contests.sql)"
Import-Sql (Join-Path $Tools "seed-contests.sql")

Step "Loading grading rules (seed-grading.sql)"
$gradingSql = Join-Path $Tools "seed-grading.sql"
# Start-Process writes the raw UTF-8 bytes; PowerShell 5.1 '>' would re-encode as UTF-16.
$gradingArgs = @($Python | Select-Object -Skip 1) + @("`"$(Join-Path $Tools 'problems\seed_grading.py')`"")
$p = Start-Process -FilePath $Python[0] -ArgumentList $gradingArgs -RedirectStandardOutput $gradingSql -NoNewWindow -Wait -PassThru
if ($p.ExitCode -ne 0) { Fail "seed_grading.py failed." }
Import-Sql $gradingSql

Step "Uploading test cases"
$login = Invoke-RestMethod -Method Post -Uri "$ApiUrl/api/v1/auth/login" -ContentType "application/json" `
    -Body (@{ usernameOrEmail = $AdminUser; password = $AdminPassword } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
foreach ($dir in Get-ChildItem $Tools -Directory | Where-Object { Test-Path (Join-Path $_.FullName "cases") } | Sort-Object Name) {
    $slug = $dir.Name
    $local = @(Get-ChildItem (Join-Path $dir.FullName "cases") -Filter *.in).Count
    try {
        $problem = Invoke-RestMethod -Uri "$ApiUrl/api/v1/problems/$slug" -Headers $headers
    } catch {
        Write-Host "skip $slug (problem not in the database)" -ForegroundColor Yellow
        continue
    }
    if ($problem.data) { $problem = $problem.data }
    # Assign first: PowerShell 5.1 sends a JSON array down the pipeline as one object.
    $testCases = Invoke-RestMethod -Uri "$ApiUrl/api/v1/problems/$($problem.id)/test-cases" -Headers $headers
    $remote = @($testCases).Count
    if ($remote -eq $local) { Write-Host "ok   $slug ($local test cases)"; continue }
    Write-Host "load $slug ($remote on server, $local local)"
    # -BypassContestLock: seeded contests use some of these problems, which blocks the normal unpublish flow.
    & (Join-Path $Tools "seed-two-sum-testcases.ps1") -Slug $slug -Username $AdminUser -Password $AdminPassword -BaseUrl $ApiUrl -BypassContestLock *> $null
    if (-not $?) { Fail "Uploading test cases for $slug failed. Run backend\tools\seed-two-sum-testcases.ps1 -Slug $slug to see the error." }
}

Step "Loading demo data (demo\demo-data.sql)"
Import-Sql (Join-Path $Tools "demo\demo-data.sql")

# ---------------------------------------------------------------- 5. Done
Write-Host ""
Write-Host "Mini LeetCode is ready (DEV ONLY)." -ForegroundColor Green
Write-Host "  Web app : http://localhost:$FrontendPort"
Write-Host "  API     : $ApiUrl"
Write-Host "  MinIO   : http://localhost:9001 (minioadmin / minioadminpassword)"
Write-Host ""
Write-Host "Accounts:"
Write-Host "  ADMIN       $AdminUser / $AdminPassword"
Write-Host "  INSTRUCTOR  demo_instructor / Demo@123456"
Write-Host "  USER        demo_student / Demo@123456"
