#!/usr/bin/env pwsh
#Requires -Version 7.0
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
    Write-Host "  启动 $Module (port $Port)..." -NoNewline
    $title = "AI Agent - $Module"
    $cmd = "mvn -pl $Module spring-boot:run -D `"spring-boot.run.profiles=dev`""
    Start-Process powershell -ArgumentList "-NoExit", "-Title", "$title", "-Command", "cd $platformDir; $cmd" -WindowStyle Normal

    $ready = Wait-ForPort -Port $Port -TimeoutSeconds 90
    if (-not $ready) {
        Write-Host " 超时" -ForegroundColor Red
        throw "$Module 在 90 秒内未就绪"
    }
    Write-Host " 就绪" -ForegroundColor Green
}

# 1. 检查环境变量
Write-Step "检查环境变量"
$envFile = Join-Path $projectRoot '.env'
if (Test-Path $envFile) {
    Write-Host "从 $envFile 加载环境变量..." -ForegroundColor DarkGray
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^([^#][^=]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
        }
    }
} else {
    Write-Host "警告：未找到 $envFile，请复制 .env.example 并配置 LLM API Key" -ForegroundColor Yellow
}

# 2. 检查端口占用
Write-Step "检查端口占用"
$allPorts = $infraPorts + ($backendServices | ForEach-Object { $_.Port }) + 3000
$conflicts = @()
foreach ($port in $allPorts) {
    if (Test-PortInUse -Port $port) {
        $proc = Get-ProcessByPort -Port $port
        $conflicts += [PSCustomObject]@{ Port = $port; Process = $proc?.ProcessName; PID = $proc?.Id }
    }
}

if ($conflicts.Count -gt 0) {
    Write-Host "以下端口已被占用：" -ForegroundColor Red
    $conflicts | Format-Table -AutoSize | Out-String | Write-Host

    if ($conflicts | Where-Object { $_.Port -eq 3306 -and $_.Process -like '*mysqld*' }) {
        Write-Host "检测到本地 MySQL 服务占用 3306。`n可以使用 -StopMySQLService 参数自动停止，或手动停止后重试。" -ForegroundColor Yellow
        if ($StopMySQLService) {
            Write-Host "正在停止 MySQL 服务..." -NoNewline
            Stop-Service -Name MySQL -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
            if (-not (Test-PortInUse -Port 3306)) {
                Write-Host " 已停止" -ForegroundColor Green
            } else {
                throw "无法停止本地 MySQL 服务，请手动处理"
            }
        } else {
            throw "端口冲突未解决，启动中止"
        }
    } else {
        throw "请释放冲突端口后再运行脚本"
    }
} else {
    Write-Host "所有端口均空闲" -ForegroundColor Green
}

# 3. 启动基础设施
if (-not $SkipInfra) {
    Write-Step "启动基础设施（Docker Compose）"
    Set-Location $projectRoot
    docker-compose up -d

    Write-Host "等待基础设施就绪..." -NoNewline
    Start-Sleep -Seconds 20
    Write-Host " 完成" -ForegroundColor Green
} else {
    Write-Host "跳过基础设施启动" -ForegroundColor DarkGray
}

# 4. 启动后端服务
if (-not $SkipBackend) {
    Write-Step "启动后端微服务"
    Set-Location $platformDir
    foreach ($svc in $backendServices) {
        Start-BackendService -Module $svc.Name -Port $svc.Port
        Start-Sleep -Seconds 2
    }
} else {
    Write-Host "跳过后端服务启动" -ForegroundColor DarkGray
}

# 5. 启动前端
if (-not $SkipFrontend) {
    Write-Step "启动前端开发服务器"
    Set-Location $webDir
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Host "  安装 npm 依赖..." -NoNewline
        npm install --silent
        Write-Host " 完成" -ForegroundColor Green
    }
    Start-Process powershell -ArgumentList "-NoExit", "-Title", "AI Agent - Web", "-Command", "cd $webDir; npm run dev" -WindowStyle Normal

    Write-Host "等待前端 http://localhost:3000 就绪..." -NoNewline
    $frontendReady = Wait-ForHttp -Url "http://localhost:3000" -TimeoutSeconds 60
    if ($frontendReady) {
        Write-Host " 就绪" -ForegroundColor Green
    } else {
        Write-Host " 超时，请手动检查" -ForegroundColor Yellow
    }
} else {
    Write-Host "跳过前端启动" -ForegroundColor DarkGray
}

Write-Step "启动完成"
Write-Host "  前端地址: http://localhost:3000"
Write-Host "  网关地址: http://localhost:8080"
Write-Host "  Nacos:    http://localhost:8848/nacos"
Write-Host "  MinIO:    http://localhost:9001"
