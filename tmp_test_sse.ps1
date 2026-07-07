Write-Host "测试 SSE 流式对话请求..."
$uri = "http://localhost:8083/api/v1/sessions/test-session-123/stream?content=你查询一个知识库中的问题并给出答案&agentId=1&kbCode=11111&modelProvider=deepseek&modelName=deepseek-v4-flash"
$headers = @{"X-User-Id" = "1"}
try {
    $resp = Invoke-WebRequest -Uri $uri -Headers $headers -TimeoutSec 60
    Write-Host "Status: $($resp.StatusCode)"
    Write-Host "Content:"
    Write-Host $resp.Content
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    Write-Host $_.Exception.Response.StatusCode.Value__
    Write-Host $_.Exception.Response.Content
}
