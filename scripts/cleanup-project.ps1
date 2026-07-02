#!/usr/bin/env pwsh
# 清理 ai-agent 项目根目录和模块目录的临时文件/目录
# 保留核心项目文件：平台、前端、脚本、文档、配置等

$root = "D:\Workspace\ai-agent"

$toDelete = @(
    # IDE / OS
    "$root\.idea",
    "$root\.uploads",
    "$root\logs",
    # Root-level test artifacts
    "$root\agent_list.json",
    "$root\chat.png",
    "$root\chat_debug.log",
    "$root\core.log",
    "$root\core_stdout.log",
    "$root\create_session.json",
    "$root\demo-summary.txt",
    "$root\login.json",
    "$root\login_response.json",
    "$root\messages.json",
    "$root\register.json",
    "$root\session_response.json",
    "$root\unify-sidebars.js",
    "$root\user.log",
    "$root\wrong_login.json",
    "$root\login-error*.json",
    "$root\login-error*.png",
    "$root\login-error*.js",
    "$root\login-page.png",
    "$root\Workspaceai-agentlogin-error.png",
    "$root\screenshots",
    # Module-level logs
    "$root\ai-agent-platform\*.log",
    "$root\ai-agent-platform\ai-agent-*.log",
    "$root\ai-agent-platform\ai-agent-common\*.log",
    "$root\ai-agent-platform\ai-agent-gateway\*.log",
    "$root\ai-agent-platform\ai-agent-user\*.log",
    "$root\ai-agent-platform\ai-agent-core\*.log",
    "$root\ai-agent-platform\ai-agent-chat\*.log",
    "$root\ai-agent-platform\ai-agent-knowledge\*.log",
    "$root\ai-agent-platform\ai-agent-file\*.log",
    "$root\ai-agent-platform\ai-agent-mcp\*.log"
)

foreach ($item in $toDelete) {
    if ($item -match '\*') {
        Get-ChildItem -Path $item -ErrorAction SilentlyContinue | ForEach-Object {
            Remove-Item -Recurse -Force -Path $_.FullName
            Write-Host "已删除: $($_.FullName)"
        }
    } elseif (Test-Path $item) {
        Remove-Item -Recurse -Force -Path $item
        Write-Host "已删除: $item"
    }
}

Write-Host "清理完成"
