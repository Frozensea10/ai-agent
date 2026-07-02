#!/usr/bin/env pwsh
#Requires -Version 7.0
<#
.SYNOPSIS
    一键停止 AI Agent 平台本地开发环境。
.DESCRIPTION
    停止前端 Node 进程、后端 Java 进程，并可选停止 Docker Compose 基础设施。
#>

param(
    [switch]$StopInfra
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

function Write-Step {
    param([string]$Message)
    Write-Host "`n[== $Message ==]" -ForegroundColor Cyan
}

Write-Step "停止前端进程"
Get-Process -Name node -ErrorAction SilentlyContinue | Stop-Process -Force
Write-Host "前端 Node 进程已停止" -ForegroundColor Green

Write-Step "停止后端 Java 进程"
$stopped = 0
Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object {
    Stop-Process -Id $_.Id -Force
    $stopped++
}
Write-Host "已停止 $stopped 个 Java 进程" -ForegroundColor Green

if ($StopInfra) {
    Write-Step "停止 Docker Compose 基础设施"
    Set-Location $projectRoot
    docker-compose down
    Write-Host "基础设施已停止" -ForegroundColor Green
} else {
    Write-Host "保留 Docker Compose 基础设施运行（加 -StopInfra 可一起停止）" -ForegroundColor DarkGray
}

Write-Step "完成"
