package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpRequestExecutorTest {

    @Mock
    private RestTemplate restTemplate;

    private HttpRequestExecutor createExecutor(String allowedDomainsConfig) {
        return new HttpRequestExecutor(restTemplate, allowedDomainsConfig);
    }

    private ToolExecuteRequest createRequest(String url, String method, Map<String, String> headers, String body) {
        ToolExecuteRequest request = new ToolExecuteRequest();
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        if (method != null) {
            params.put("method", method);
        }
        if (headers != null) {
            params.put("headers", headers);
        }
        if (body != null) {
            params.put("body", body);
        }
        request.setParameters(params);
        return request;
    }

    @Test
    @DisplayName("URL 为空返回错误")
    void shouldReturnErrorWhenUrlEmpty() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        ToolExecuteRequest request = createRequest("", "GET", null, null);

        ToolExecuteResult result = executor.execute(request);

        assertFalse(result.isSuccess());
        assertEquals("URL不能为空", result.getErrorMessage());
        assertNotNull(result.getExecuteTimeMs());
    }

    @Test
    @DisplayName("不支持的 HTTP 方法返回错误")
    void shouldReturnErrorWhenMethodNotAllowed() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        ToolExecuteRequest request = createRequest("https://allowed.com/api", "DELETE", null, null);

        ToolExecuteResult result = executor.execute(request);

        assertFalse(result.isSuccess());
        assertEquals("不支持的HTTP方法: DELETE", result.getErrorMessage());
    }

    @Test
    @DisplayName("域名不在白名单返回错误")
    void shouldReturnErrorWhenDomainNotAllowed() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        ToolExecuteRequest request = createRequest("https://example.com/api", "GET", null, null);

        ToolExecuteResult result = executor.execute(request);

        assertFalse(result.isSuccess());
        assertEquals("该域名不在白名单中，禁止访问", result.getErrorMessage());
    }

    @Test
    @DisplayName("URL 格式非法返回错误")
    void shouldReturnErrorWhenUrlInvalid() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        ToolExecuteRequest request = createRequest("not-a-url", "GET", null, null);

        ToolExecuteResult result = executor.execute(request);

        assertFalse(result.isSuccess());
        assertEquals("该域名不在白名单中，禁止访问", result.getErrorMessage());
    }

    @Test
    @DisplayName("子域名命中白名单")
    void shouldAllowSubdomain() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        ToolExecuteRequest request = createRequest("https://sub.allowed.com/api", "GET", null, null);

        when(restTemplate.exchange(eq("https://sub.allowed.com/api"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("response"));

        ToolExecuteResult result = executor.execute(request);

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("localhost 自动放行")
    void shouldAllowLocalhost() {
        HttpRequestExecutor executor = createExecutor("");
        ToolExecuteRequest request = createRequest("http://svc.localhost/api", "GET", null, null);

        when(restTemplate.exchange(eq("http://svc.localhost/api"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("response"));

        ToolExecuteResult result = executor.execute(request);

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("GET 请求成功并过滤非法请求头")
    void shouldExecuteGetRequestSuccessfully() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Api-Key", "secret");
        headers.put("Bad-Header", "should-be-filtered");
        ToolExecuteRequest request = createRequest("https://allowed.com/api", "GET", headers, null);

        when(restTemplate.exchange(eq("https://allowed.com/api"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("body"));

        ToolExecuteResult result = executor.execute(request);

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(200, data.get("statusCode"));
        assertEquals("body", data.get("body"));
    }

    @Test
    @DisplayName("POST 请求成功并携带 body")
    void shouldExecutePostRequestWithBody() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        ToolExecuteRequest request = createRequest("https://allowed.com/api", "POST", null, "{\"id\":1}");

        when(restTemplate.exchange(eq("https://allowed.com/api"), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("created"));

        ToolExecuteResult result = executor.execute(request);

        assertTrue(result.isSuccess());
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(200, data.get("statusCode"));
        assertEquals("created", data.get("body"));
    }

    @Test
    @DisplayName("RestTemplate 异常返回错误")
    void shouldReturnErrorWhenRestTemplateFails() {
        HttpRequestExecutor executor = createExecutor("allowed.com");
        ToolExecuteRequest request = createRequest("https://allowed.com/api", "GET", null, null);

        when(restTemplate.exchange(eq("https://allowed.com/api"), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection timeout"));

        ToolExecuteResult result = executor.execute(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().startsWith("请求异常:"));
        assertTrue(result.getErrorMessage().contains("connection timeout"));
    }
}
