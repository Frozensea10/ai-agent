#!/usr/bin/env pwsh
# 清理 ai-agent 项目根目录的临时文件/目录
# 保留核心项目文件：平台、前端、脚本、文档、配置等

$root = "D:\Workspace\ai-agent"

$toDelete = @(
    "$root\.idea",
    "$root\.uploads",
    "$root\logs",
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
    "$root\screenshots"
)

foreach ($item in $toDelete) {
    if (Test-Path $item) {
        Remove-Item -Recurse -Force -Path $item
        Write-Host "已删除: $item"
    }
}

Write-Host "清理完成"