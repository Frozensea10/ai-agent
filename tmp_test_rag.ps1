$uri = "http://localhost:8084/api/v1/knowledge-bases/11111/rag"
$body = @{ query = "查询知识库中的问题"; topK = 10 } | ConvertTo-Json -Compress
$headers = @{
    "Content-Type" = "application/json"
    "X-User-Id" = "1"
}
try {
    $resp = Invoke-WebRequest -Uri $uri -Method POST -Body $body -Headers $headers -TimeoutSec 60
    Write-Host "Status: $($resp.StatusCode)"
    Write-Host $resp.Content
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode.Value__)"
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $reader.ReadToEnd()
    }
}
