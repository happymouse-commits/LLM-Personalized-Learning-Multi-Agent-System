# ============================================================
# CRYD - One Click Start Script
# Usage: powershell -ExecutionPolicy Bypass -File start.ps1
# ============================================================

$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
# Avoid hardcoding Chinese path — find the backend dir by looking for pom.xml
$BackendDir = $null
Get-ChildItem -Path $Root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
    if (Test-Path (Join-Path $_.FullName "pom.xml")) { $BackendDir = $_.FullName }
}
if (-not $BackendDir) { $BackendDir = Join-Path $Root "后端" }
$FrontendDir = Join-Path $Root "frontend"
$JarFile = Join-Path $BackendDir "target\cryd-1.0.0.jar"

function Write-Step  { param([string]$msg) Write-Host "`n[>>] $msg" -ForegroundColor Cyan }
function Write-OK    { param([string]$msg) Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn  { param([string]$msg) Write-Host "  [!!] $msg" -ForegroundColor Yellow }
function Write-Fail  { param([string]$msg) Write-Host "  [XX] $msg" -ForegroundColor Red }

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "   CRYD - One Click Start" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

# ---- 0. Auto-detect and add tools to PATH ----
Write-Step "Detecting tools..."

# Detect Java
$javaPath = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $javaPath) {
    $candidates = @("E:\JDK", "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Microsoft")
    foreach ($dir in $candidates) {
        $found = Get-ChildItem -Path $dir -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $javaPath = $found.FullName; break }
    }
}
if ($javaPath) {
    $javaHome = Split-Path -Parent (Split-Path -Parent $javaPath)
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
    $javaVer = & java -version 2>&1 | Select-String "version" | Select-Object -First 1
    Write-OK "Java: $javaVer"
} else {
    Write-Fail "java not found, install JDK 21+"
    exit 1
}

# Detect Node.js
$nodePath = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $nodePath) {
    $candidates = @("C:\Program Files\nodejs", "E:\nodejs")
    foreach ($dir in $candidates) {
        $found = Get-ChildItem -Path $dir -Filter "node.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $nodePath = $found.FullName; break }
    }
}
if ($nodePath) {
    $nodeVer = & node -v 2>&1
    Write-OK "Node.js: $nodeVer"
} else {
    Write-Fail "node not found, install Node.js 18+"
    exit 1
}

# Detect npm
$npmPath = (Get-Command npm -ErrorAction SilentlyContinue).Source
if ($npmPath) {
    $npmVer = & npm -v 2>&1
    Write-OK "npm: $npmVer"
} else {
    Write-Fail "npm not found"
    exit 1
}

# ---- 1. Check MySQL ----
Write-Step "Checking MySQL..."
$mysqlRunning = $false

# Read .env to get MySQL password
$envFile = Join-Path $Root ".env"
$mysqlPassword = ""
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^MYSQL_PASSWORD=(.+)$') { $mysqlPassword = $Matches[1] }
        if ($_ -match '^MYSQL_USERNAME=(.+)$') { $mysqlUser = $Matches[1] }
    }
}
if (-not $mysqlUser) { $mysqlUser = "root" }

# Find mysql.exe
$mysqlExe = $null
$mysqlCandidates = @(
    "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe",
    "C:\Program Files\MySQL\MySQL Server 9.1\bin\mysql.exe"
)
foreach ($candidate in $mysqlCandidates) {
    if (Test-Path $candidate) { $mysqlExe = $candidate; break }
}
if (-not $mysqlExe) {
    $found = Get-ChildItem "C:\Program Files\MySQL" -Filter "mysql.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $mysqlExe = $found.FullName }
}

if ($mysqlExe) {
    # Test connection with password from .env
    $pwArg = if ($mysqlPassword) { "--password=$mysqlPassword" } else { "--password=" }
    $testResult = & $mysqlExe -u $mysqlUser -P 3306 $pwArg -e "SELECT 1" 2>&1
    if ($LASTEXITCODE -eq 0) {
        $mysqlRunning = $true
        Write-OK "MySQL running (port 3306, user=$mysqlUser)"
    } else {
        # Try without password
        $testResult = & $mysqlExe -u $mysqlUser -P 3306 --password="" -e "SELECT 1" 2>&1
        if ($LASTEXITCODE -eq 0) {
            $mysqlRunning = $true
            Write-OK "MySQL running (port 3306, no password)"
        }
    }
}

if (-not $mysqlRunning) {
    Write-Warn "MySQL not running, trying to start service..."
    try {
        $svc = Get-Service -Name "MySQL*" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($svc) {
            try {
                Start-Service $svc.Name -ErrorAction Stop
                Start-Sleep -Seconds 3
                if ($mysqlExe) {
                    $pwArg = if ($mysqlPassword) { "--password=$mysqlPassword" } else { "--password=" }
                    & $mysqlExe -u $mysqlUser -P 3306 $pwArg -e "SELECT 1" 2>&1 | Out-Null
                    if ($LASTEXITCODE -eq 0) { $mysqlRunning = $true }
                }
            } catch {
                Write-Warn "Cannot start service (try running as Administrator next time)"
                if ($mysqlExe) {
                    $pwArg = if ($mysqlPassword) { "--password=$mysqlPassword" } else { "--password=" }
                    & $mysqlExe -u $mysqlUser -P 3306 $pwArg -e "SELECT 1" 2>&1 | Out-Null
                    if ($LASTEXITCODE -eq 0) { $mysqlRunning = $true }
                }
            }
            if ($mysqlRunning) {
                Write-OK "MySQL connected"
            } else {
                Write-Fail "MySQL not reachable. Start it manually:"
                Write-Host "    1) Run 'net start MySQL84' as Administrator" -ForegroundColor Yellow
                Write-Host "    2) Or open MySQL Workbench and start the server" -ForegroundColor Yellow
                Write-Host "    3) Then re-run this script" -ForegroundColor Yellow
                Write-Host "  Project will try H2 fallback if configured" -ForegroundColor Gray
            }
        } else {
            Write-Fail "MySQL service not found. Install MySQL or configure H2 fallback"
        }
    } catch {
        Write-Warn "MySQL check failed, will use H2 fallback if configured"
    }
}

# ---- 2. Check database ----
if ($mysqlRunning -and $mysqlExe) {
    Write-Step "Checking database 'cryd'..."
    $pwArg = if ($mysqlPassword) { "--password=$mysqlPassword" } else { "--password=" }
    $dbCheck = & $mysqlExe -u $mysqlUser -P 3306 $pwArg -e "USE cryd; SELECT 1" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Database 'cryd' not found, creating..."
        & $mysqlExe -u $mysqlUser -P 3306 $pwArg -e "CREATE DATABASE IF NOT EXISTS cryd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" 2>&1
        Write-OK "Database 'cryd' created"
    } else {
        Write-OK "Database 'cryd' exists"
    }
} else {
    Write-Warn "MySQL unavailable, backend will use H2 fallback if configured"
}

# ---- 3. Install frontend deps ----
Write-Step "Installing frontend dependencies..."
Push-Location $FrontendDir
if (-not (Test-Path "node_modules")) {
    Write-Host "  First run, npm install ..." -ForegroundColor Gray
    & npm install 2>&1 | Out-Null
    Write-OK "Frontend deps installed"
} else {
    Write-OK "node_modules exists, skip"
}
Pop-Location

# ---- 4. Cleanup stale processes (by port) ----
Write-Step "Cleaning up stale processes..."
$stalePorts = @(8090, 5173, 5174)
foreach ($port in $stalePorts) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn -and $conn.OwningProcess -gt 0) {
            $p = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
            if ($p) {
                Write-Warn "Killing stale process on port $port`: $($p.ProcessName) (PID: $($p.Id))"
                Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        # Get-NetTCPConnection may need admin - fallback to netstat
        $netstat = netstat -ano | Select-String ":$port\s"
        if ($netstat) {
            $parts = $netstat.ToString() -split '\s+'
            $pid = $parts[-1]
            if ($pid -gt 0) {
                Write-Warn "Killing stale process on port $port (PID: $pid)"
                Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            }
        }
    }
}
Start-Sleep -Seconds 2
Write-OK "Stale processes cleaned"

# ---- 5. Start backend (java -jar or mvn spring-boot:run) ----
Write-Step "Starting backend (Spring Boot, port 8090)..."

$useMvnRun = $false
if (-not (Test-Path $JarFile)) {
    Write-Warn "JAR not found: $JarFile"
    # Check if mvn is available
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnCmd) {
        Write-Host "  Using 'mvn spring-boot:run' instead..." -ForegroundColor Yellow
        $useMvnRun = $true
    } else {
        Write-Host "  Attempting to build with Maven..." -ForegroundColor Yellow
        Push-Location $BackendDir
        & mvn package -DskipTests -B -q 2>&1
        Pop-Location
        if (-not (Test-Path $JarFile)) {
            Write-Fail "Build failed and mvn not found, cannot start backend"
            exit 1
        }
        Write-OK "Backend built successfully"
    }
}

$backendLog = Join-Path $Root "backend.log"
$backendErrLog = Join-Path $Root "backend_err.log"

if ($useMvnRun) {
    $backendJob = Start-Process -FilePath "mvn" `
        -ArgumentList "spring-boot:run", "-Dspring-boot.run.jvmArguments=`"-Dserver.port=8090`"" `
        -WorkingDirectory $BackendDir `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErrLog `
        -NoNewWindow `
        -PassThru
} else {
    $backendJob = Start-Process -FilePath "java" `
        -ArgumentList "-jar `"$JarFile`" --server.port=8090" `
        -WorkingDirectory $BackendDir `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErrLog `
        -NoNewWindow `
        -PassThru
}

Write-OK "Backend starting (PID: $($backendJob.Id))"

# Wait for backend ready — poll port until it accepts connections (max 180s)
Write-Host "  Waiting for backend to fully start..." -ForegroundColor Gray -NoNewline
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 3
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("localhost", 8090)
        if ($tcp.Connected) {
            $tcp.Close()
            $ready = $true
            break
        }
    } catch {}
    Write-Host "." -ForegroundColor Gray -NoNewline
}
Write-Host ""

if ($ready) {
    Write-OK "Backend fully ready (port 8090 listening)"
} else {
    Write-Warn "Backend not ready yet, starting frontend anyway (some features may load slowly)"
}

# ---- 6. Start frontend (npm run dev) ----
Write-Step "Starting frontend (Vite, port 5174)..."

$frontendLog = Join-Path $Root "frontend.log"
$frontendErrLog = Join-Path $Root "frontend_err.log"
$frontendJob = Start-Process -FilePath "powershell" `
    -ArgumentList "-NoProfile -Command npm run dev" `
    -WorkingDirectory $FrontendDir `
    -RedirectStandardOutput $frontendLog `
    -RedirectStandardError $frontendErrLog `
    -NoNewWindow `
    -PassThru

Write-OK "Frontend starting (PID: $($frontendJob.Id))"

# Wait for frontend ready
Write-Host "  Waiting for frontend..." -ForegroundColor Gray -NoNewline
$frontendReady = $false
for ($i = 1; $i -le 15; $i++) {
    Start-Sleep -Seconds 2
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:5174/" -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($resp.StatusCode) {
            $frontendReady = $true
            break
        }
    } catch {}
    Write-Host "." -ForegroundColor Gray -NoNewline
}
Write-Host ""

if ($frontendReady) {
    Write-OK "Frontend ready (http://localhost:5174)"
} else {
    Write-Warn "Frontend not ready yet, check frontend.log"
    Write-Host "  Last 10 lines of frontend.log:" -ForegroundColor Gray
    Get-Content $frontendLog -Tail 10 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
}

# ---- 7. Open browser ----
Write-Step "Opening browser..."
Start-Process "http://localhost:5174/login.html"
Write-OK "Browser opened"

# ---- 8. Done ----
Write-Host "`n========================================" -ForegroundColor Green
Write-Host "   All services started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontend:  http://localhost:5174" -ForegroundColor White
Write-Host "  Backend:   http://localhost:8090" -ForegroundColor White
Write-Host ""
Write-Host "  Backend PID:  $($backendJob.Id)" -ForegroundColor Gray
Write-Host "  Frontend PID: $($frontendJob.Id)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Press Ctrl+C to stop all services" -ForegroundColor Yellow
Write-Host ""

# ---- 9. Wait for exit and cleanup ----
try {
    Write-Host "Running... Press Ctrl+C to exit" -ForegroundColor Cyan
    while ($true) {
        if ($backendJob.HasExited -and $frontendJob.HasExited) {
            Write-Warn "Both processes exited"
            break
        }
        if ($backendJob.HasExited) {
            Write-Warn "Backend process exited (check backend.log)"
        }
        if ($frontendJob.HasExited) {
            Write-Warn "Frontend process exited (check frontend.log)"
        }
        Start-Sleep -Seconds 5
    }
} finally {
    Write-Host "`nStopping services..." -ForegroundColor Yellow
    if (-not $backendJob.HasExited) {
        Stop-Process -Id $backendJob.Id -Force -ErrorAction SilentlyContinue
        Write-OK "Backend stopped"
    }
    if (-not $frontendJob.HasExited) {
        Stop-Process -Id $frontendJob.Id -Force -ErrorAction SilentlyContinue
        Write-OK "Frontend stopped"
    }
    Write-Host "Done!" -ForegroundColor Green
}
