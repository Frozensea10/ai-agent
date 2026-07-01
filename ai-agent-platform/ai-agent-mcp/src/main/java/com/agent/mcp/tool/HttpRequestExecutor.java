package com.agent.mcp.tool;

import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Slf4j
@Component
public class HttpRequestExecutor implements BuiltInToolExecutor {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String DEFAULT_LOCALHOST_SUFFIX = ".localhost";
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST");
    private static final Set<String> ALLOWED_HEADERS = Set.of(
            "Content-Type", "Accept", "Authorization", "X-Api-Key", "X-Request-Id"
    );
    private static final String PARAM_URL = "url";
    private static final String PARAM_METHOD = "method";
    private static final String PARAM_HEADERS = "headers";
    private static final String PARAM_BODY = "body";
    private static final String DEFAULT_METHOD = "GET";

    private final RestTemplate restTemplate;
    private final Set<String> allowedDomains;

    public HttpRequestExecutor(RestTemplate restTemplate,
                               @Value("${mcp.http.allowed-domains:}") String allowedDomainsConfig) {
        this.restTemplate = restTemplate;
        this.allowedDomains = parseAllowedDomains(allowedDomainsConfig);
    }

    private Set<String> parseAllowedDomains(String config) {
        if (config == null || config.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> domains = new HashSet<>();
        for (String domain : config.split(",")) {
            String trimmed = domain.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                domains.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(domains);
    }

    @Override
    public String getToolCode() {
        return "http_request";
    }

    @Override
    public String getToolName() {
        return "HTTP请求";
    }

    @Override
    public String getDescription() {
        return "发送HTTP GET/POST请求，返回响应内容。仅支持白名单域名。";
    }

    @Override
    public String getConfigSchema() {
        return "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\",\"description\":\"请求URL\"},\"method\":{\"type\":\"string\",\"enum\":[\"GET\",\"POST\"],\"default\":\"GET\"},\"headers\":{\"type\":\"object\",\"description\":\"请求头\"},\"body\":{\"type\":\"string\",\"description\":\"请求体（POST时有效）\"}},\"required\":[\"url\"]}";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        ToolExecuteResult result = new ToolExecuteResult();
        result.setToolCode(getToolCode());

        try {
            Map<String, Object> params = request.getParameters();
            String url = (String) params.get(PARAM_URL);
            String method = (String) params.getOrDefault(PARAM_METHOD, DEFAULT_METHOD);
            Map<String, String> headers = (Map<String, String>) params.getOrDefault(PARAM_HEADERS, Collections.emptyMap());
            String body = (String) params.get(PARAM_BODY);

            if (url == null || url.isBlank()) {
                result.setSuccess(false);
                result.setErrorMessage("URL不能为空");
                return finishResult(result, startTime);
            }

            String normalizedMethod = method.toUpperCase();
            if (!ALLOWED_METHODS.contains(normalizedMethod)) {
                result.setSuccess(false);
                result.setErrorMessage("不支持的HTTP方法: " + method);
                return finishResult(result, startTime);
            }

            if (!isAllowedDomain(url)) {
                result.setSuccess(false);
                result.setErrorMessage("该域名不在白名单中，禁止访问");
                return finishResult(result, startTime);
            }

            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach((key, value) -> {
                if (ALLOWED_HEADERS.contains(key)) {
                    httpHeaders.add(key, value);
                }
            });
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(normalizedMethod), entity, String.class);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("statusCode", response.getStatusCode().value());
            data.put("headers", response.getHeaders());
            data.put("body", response.getBody());

            result.setSuccess(true);
            result.setData(data);

        } catch (Exception e) {
            log.error("HTTP请求异常", e);
            result.setSuccess(false);
            result.setErrorMessage("请求异常: " + e.getMessage());
        }

        return finishResult(result, startTime);
    }

    private ToolExecuteResult finishResult(ToolExecuteResult result, long startTime) {
        result.setExecuteTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private boolean isAllowedDomain(String url) {
        try {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost().toLowerCase();
            if (allowedDomains.contains(host) || host.endsWith(DEFAULT_LOCALHOST_SUFFIX)) {
                return true;
            }
            for (String domain : allowedDomains) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return true;
                }
            }
            return false;
        } catch (MalformedURLException e) {
            log.warn("URL格式不正确: {}", url, e);
            return false;
        }
    }
}
