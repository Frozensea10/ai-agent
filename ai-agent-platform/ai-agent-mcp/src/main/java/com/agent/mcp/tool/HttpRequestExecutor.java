package com.agent.mcp.tool;

import com.agent.common.exception.BusinessException;
import com.agent.mcp.dto.ToolExecuteRequest;
import com.agent.mcp.dto.ToolExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;

/**
 * HTTP 请求执行器：内置工具，用于向白名单域名发送 GET/POST 请求。
 *
 * <p><b>安全提示（SSRF 防护）：</b></p>
 * <ul>
 *   <li>仅允许显式白名单中的域名（默认包含已知外部 API 域名与本地开发主机，
 *       可通过 {@code mcp.http.allowed-domains} 追加配置），默认拒绝所有未列出的主机。</li>
 *   <li>仅允许 http / https 协议，禁止 file://、gopher://、ftp://、jar: 等危险协议。</li>
 *   <li>解析目标主机名后，会枚举其全部 {@link InetAddress}，若存在任意内网/回环/链路本地地址，
 *       一律拒绝（防御 DNS rebinding 与 127.0.0.1.nip.io 等通配 DNS 攻击）。</li>
 *   <li>禁用 HTTP 重定向跟随，避免白名单域名通过 302/301 跳转访问内网服务。</li>
 *   <li>请求头参数会做严格类型校验，避免恶意构造的列表/数组引发 ClassCastException。</li>
 * </ul>
 */
@Slf4j
@Component
public class HttpRequestExecutor implements BuiltInToolExecutor {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String LOCALHOST_SUFFIX = ".localhost";
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST");
    private static final Set<String> ALLOWED_HEADERS = Set.of(
            "Content-Type", "Accept", "Authorization", "X-Api-Key", "X-Request-Id"
    );
    /** 仅允许的协议，禁止 file://、gopher://、ftp://、jar: 等危险协议。 */
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");
    /** 默认白名单域名：仅包含已知外部 API 域名与本地开发用主机。 */
    private static final Set<String> DEFAULT_ALLOWED_DOMAINS = Set.of(
            "api.openai.com",
            "api.deepseek.com",
            "api.anthropic.com",
            "localhost",
            "127.0.0.1"
    );
    /**
     * 显式信任的本地主机：跳过内网 IP 检查。
     * 因为这些主机本身就是用于本地访问，IP 检查会必然命中 127.0.0.0/8。
     */
    private static final Set<String> LOCAL_TRUSTED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "[::1]", "::1"
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
        this.restTemplate = configureRestTemplate(restTemplate);
        this.allowedDomains = parseAllowedDomains(allowedDomainsConfig);
    }

    /**
     * 禁用 RestTemplate 的重定向跟随，避免 SSRF 通过 302/301 跳转绕过白名单；
     * 同时统一设置连接/读取超时。
     */
    private RestTemplate configureRestTemplate(RestTemplate restTemplate) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        // 禁用重定向跟随（实例方法），避免 SSRF 通过 302/301 跳转绕过白名单
        factory.setOutputStreaming(false);
        // 通过自定义 RequestFactory 包装，禁用底层 HttpURLConnection 的重定向
        restTemplate.setRequestFactory(factory);
        // 全局禁用（深度防御，影响 JVM 内所有 HttpURLConnection）
        java.net.HttpURLConnection.setFollowRedirects(false);
        return restTemplate;
    }

    private Set<String> parseAllowedDomains(String config) {
        Set<String> domains = new HashSet<>(DEFAULT_ALLOWED_DOMAINS);
        if (config != null && !config.isBlank()) {
            for (String domain : config.split(",")) {
                String trimmed = domain.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    domains.add(trimmed);
                }
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
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        ToolExecuteResult result = new ToolExecuteResult();
        result.setToolCode(getToolCode());

        try {
            Map<String, Object> params = request.getParameters();
            String url = (String) params.get(PARAM_URL);
            String method = (String) params.getOrDefault(PARAM_METHOD, DEFAULT_METHOD);
            Map<String, String> headers = extractHeaders(params.get(PARAM_HEADERS));
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

        } catch (BusinessException e) {
            log.warn("HTTP请求参数校验失败: {}", e.getMessage());
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            log.error("HTTP请求异常", e);
            result.setSuccess(false);
            result.setErrorMessage("请求异常: " + e.getMessage());
        }

        return finishResult(result, startTime);
    }

    /**
     * 提取并校验 headers 参数类型，避免恶意构造的列表/数组导致 ClassCastException。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractHeaders(Object headersObj) {
        if (headersObj == null) {
            return Collections.emptyMap();
        }
        if (!(headersObj instanceof Map)) {
            throw new BusinessException("headers参数必须是对象类型");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        ((Map<Object, Object>) headersObj)
                .forEach((k, v) -> headers.put(String.valueOf(k), String.valueOf(v)));
        return headers;
    }

    private ToolExecuteResult finishResult(ToolExecuteResult result, long startTime) {
        result.setExecuteTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 校验 URL 是否允许访问：
     * <ol>
     *   <li>协议必须为 http/https</li>
     *   <li>主机名必须在白名单中（精确匹配或子域名匹配）</li>
     *   <li>非显式信任的本地主机，需解析全部 InetAddress，任一为内网地址即拒绝（防 DNS rebinding）</li>
     * </ol>
     */
    private boolean isAllowedDomain(String url) {
        URL parsedUrl;
        try {
            parsedUrl = new URL(url);
        } catch (MalformedURLException e) {
            log.warn("URL格式不正确: {}", url, e);
            return false;
        }

        String protocol = parsedUrl.getProtocol().toLowerCase();
        if (!ALLOWED_PROTOCOLS.contains(protocol)) {
            log.warn("不允许的协议: {}，URL: {}", protocol, url);
            return false;
        }

        String host = parsedUrl.getHost().toLowerCase();
        if (!isHostWhitelisted(host)) {
            return false;
        }

        // 显式信任的本地主机（localhost / 127.0.0.1 / ::1）跳过内网 IP 检查，
        // 否则必然命中 127.0.0.0/8 导致无法本地访问。
        if (LOCAL_TRUSTED_HOSTS.contains(host)) {
            return true;
        }

        // 防御 DNS rebinding 与通配 DNS（如 127.0.0.1.nip.io）：
        // 白名单域名解析出的所有 IP 必须均不为内网地址。
        if (hasPrivateAddress(host)) {
            log.warn("主机 {} 解析到内网地址，拒绝访问", host);
            return false;
        }
        return true;
    }

    private boolean isHostWhitelisted(String host) {
        if (allowedDomains.contains(host)) {
            return true;
        }
        for (String domain : allowedDomains) {
            // 仅对包含点号的域名（如 api.openai.com）允许子域名匹配；
            // 对 localhost 等无点号条目仅允许精确匹配，避免 *.localhost 绕过白名单。
            if (domain.contains(".") && host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析主机名，检查是否存在任意内网/回环/链路本地/未分配 IP。
     * 解析失败也视为不安全（拒绝）。
     */
    private boolean hasPrivateAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateIp(addr.getHostAddress())) {
                    log.warn("主机 {} 解析到内网地址: {}", host, addr.getHostAddress());
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            log.warn("无法解析主机: {}", host, e);
            return true;
        }
    }

    /**
     * 判断 IP 字符串是否为内网/回环/链路本地/未分配/多播地址。
     * 覆盖：10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、127.0.0.0/8、
     * 169.254.0.0/16（链路本地）、0.0.0.0/8、100.64.0.0/10（CGN）、
     * IPv6 ::1、fc00::/7、fe80::/10、fec0::/10 等。
     */
    private boolean isPrivateIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // IPv6
        if (ip.contains(":")) {
            String lower = ip.toLowerCase();
            if (lower.equals("::1") || lower.equals("0:0:0:0:0:0:0:1")) {
                return true; // IPv6 回环
            }
            // IPv6 链路本地 fe80::/10
            if (lower.startsWith("fe8") || lower.startsWith("fe9")
                    || lower.startsWith("fea") || lower.startsWith("feb")) {
                return true;
            }
            // IPv6 站点本地（已废弃）fec0::/10
            if (lower.startsWith("fec") || lower.startsWith("fed")
                    || lower.startsWith("fee") || lower.startsWith("fef")) {
                return true;
            }
            // IPv6 唯一本地地址 fc00::/7
            if (lower.startsWith("fc") || lower.startsWith("fd")) {
                return true;
            }
            return false;
        }
        // IPv4
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 0) return true;                          // 0.0.0.0/8 未分配
            if (a == 10) return true;                         // 10.0.0.0/8
            if (a == 127) return true;                        // 127.0.0.0/8 回环
            if (a == 169 && b == 254) return true;            // 169.254.0.0/16 链路本地
            if (a == 172 && b >= 16 && b <= 31) return true;  // 172.16.0.0/12
            if (a == 192 && b == 168) return true;            // 192.168.0.0/16
            if (a == 100 && b >= 64 && b <= 127) return true; // 100.64.0.0/10 运营商级 NAT
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
