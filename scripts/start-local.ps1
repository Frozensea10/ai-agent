#!/usr/bin/env powershell
#Requires -Version 5.1
<#
.SYNOPSIS
    一键启动 AI Agent 平台本地开发环境。
.DESCRIPTION
    该脚本会按顺序启动：
    1. 基础设施（docker-compose）
    2. 后端微服务（gateway -> user -> core -> chat -> knowledge -> mcp -> file）
    3. 前端 dev server
    并检测端口冲突。
#>

param(
    [switch]$SkipInfra,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$StopMySQLService
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$platformDir = Join-Path $projectRoot 'ai-agent-platform'
$webDir = Join-Path $projectRoot 'ai-agent-web'

$infraPorts = @(3306, 6379, 6333, 9000, 9001, 8848, 9848, 5672, 15672)
$backendServices = @(
    @{ Name = 'ai-agent-gateway'; Port = 8080 },
    @{ Name = 'ai-agent-user'; Port = 8081 },
    @{ Name = 'ai-agent-core'; Port = 8082 },
    @{ Name = 'ai-agent-chat'; Port = 8083 },
    @{ Name = 'ai-agent-knowledge'; Port = 8084 },
    @{ Name = 'ai-agent-mcp'; Port = 8086 },
    @{ Name = 'ai-agent-file'; Port = 8085 }
)

function Write-Step {
    param([string]$Message)
    Write-Host "`n[== $Message ==]" -ForegroundColor Cyan
}

function Test-PortInUse {
    param([int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return $conn -ne $null
}

function Get-ProcessByPort {
    param([int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) {
        return Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
    }
    return $null
}

function Wait-ForPort {
    param([int]$Port, [int]$TimeoutSeconds = 90)
    $start = Get-Date
    while ((Get-Date) - $start -lt (New-TimeSpan -Seconds $TimeoutSeconds)) {
        if (Test-PortInUse -Port $Port) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-ForHttp {
    param([string]$Url, [int]$TimeoutSeconds = 60)
    $start = Get-Date
    while ((Get-Date) - $start -lt (New-TimeSpan -Seconds $TimeoutSeconds)) {
        try {
            $resp = Invoke-WebRequest -Uri $Url -Method GET -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            if ($resp.StatusCode -eq 200) { return $true }
        } catch {
            # ignore
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Start-BackendService {
    param([string]$Module, [int]$Port)
    Write-Host "  Start $Module (port $Port)..." -NoNewline
    $title = "AI Agent - $Module"
    $cmd = "mvn -pl $Module spring-boot:run -D `"spring-boot.run.profiles=dev`""
    Start-Process powershell -ArgumentList "-NoExit", "-Title", "$title", "-Command", "cd $platformDir; $cmd" -WindowStyle Normal

    $ready = Wait-ForPort -Port $Port -TimeoutSeconds 90
    if (-not $ready) {
        Write-Host " timeout" -ForegroundColor Red
        throw "$Module not ready in 90s"
    }
    Write-Host " ready" -ForegroundColor Green
}

# 1. 检查环境变量
Write-Step "Check environment"
$envFile = Join-Path $projectRoot '.env'
if (Test-Path $envFile) {
    Write-Host "Loading $envFile..." -ForegroundColor DarkGray
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^([^#][^=]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
        }
    }
} else {
    Write-Host "Warning: $envFile not found, copy .env.example and set LLM API Key" -ForegroundColor Yellow
}

# 2. 检查端口占用
Write-Step "Check ports"
$allPorts = $infraPorts + ($backendServices | ForEach-Object { $_.Port }) + 3000
$conflicts = @()
foreach ($port in $allPorts) {
    if (Test-PortInUse -Port $port) {
        $proc = Get-ProcessByPort -Port $port
        $procName = if ($proc) { $proc.ProcessName } else { $null }
        $procId = if ($proc) { $proc.Id } else { $null }
        $conflicts += [PSCustomObject]@{ Port = $port; Process = $procName; PID = $procId }
    }
}

if ($conflicts.Count -gt 0) {
    Write-Host "Ports already in use:" -ForegroundColor Red
    $conflicts | Format-Table -AutoSize | Out-String | Write-Host

    if ($conflicts | Where-Object { $_.Port -eq 3306 -and $_.Process -like '*mysqld*' }) {
        Write-Host "Local MySQL service uses 3306. Use -StopMySQLService to stop it." -ForegroundColor Yellow
        if ($StopMySQLService) {
            Write-Host "Stopping MySQL service..." -NoNewline
            Stop-Service -Name MySQL -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
            if (-not (Test-PortInUse -Port 3306)) {
                Write-Host " stopped" -ForegroundColor Green
            } else {
                throw "Cannot stop local MySQL service"
            }
        } else {
            throw "Port conflicts not resolved"
        }
    } else {
        throw "Please release conflicting ports"
    }
} else {
    Write-Host "All ports free" -ForegroundColor Green
}

# 3. 启动基础设施
if (-not $SkipInfra) {
    Write-Step "Start infrastructure (Docker Compose)"
    Set-Location $projectRoot
    docker-compose up -d

    Write-Host "Waiting for infrastructure..." -NoNewline
    Start-Sleep -Seconds 20
    Write-Host " done" -ForegroundColor Green
} else {
    Write-Host "Skip infrastructure" -ForegroundColor DarkGray
}

# 4. 启动后端服务
if (-not $SkipBackend) {
    Write-Step "Start backend services"
    Set-Location $platformDir
    foreach ($svc in $backendServices) {
        Start-BackendService -Module $svc.Name -Port $svc.Port
        Start-Sleep -Seconds 2
    }
} else {
    Write-Host "Skip backend" -ForegroundColor DarkGray
}

# 5. 启动前端
if (-not $SkipFrontend) {
    Write-Step "Start frontend dev server"
    Set-Location $webDir
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Host "  Install npm dependencies..." -NoNewline
        npm install --silent
        Write-Host " done" -ForegroundColor Green
    }
    Start-Process powershell -ArgumentList "-NoExit", "-Title", "AI Agent - Web", "-Command", "cd $webDir; npm run dev" -WindowStyle Normal

    Write-Host "Waiting for http://localhost:3000 ..." -NoNewline
    $frontendReady = Wait-ForHttp -Url "http://localhost:3000" -TimeoutSeconds 60
    if ($frontendReady) {
        Write-Host " ready" -ForegroundColor Green
    } else {
        Write-Host " timeout, check manually" -ForegroundColor Yellow
    }
} else {
    Write-Host "Skip frontend" -ForegroundColor DarkGray
}

Write-Step "Startup complete"
Write-Host "  Frontend: http://localhost:3000"
Write-Host "  Gateway:  http://localhost:8080"
Write-Host "  Nacos:    http://localhost:8848/nacos"
Write-Host "  MinIO:    http://localhost:9001"
