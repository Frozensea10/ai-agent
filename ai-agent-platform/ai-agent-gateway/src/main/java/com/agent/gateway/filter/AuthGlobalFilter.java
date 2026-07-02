package com.agent.gateway.filter;

import com.agent.common.result.Result;
import com.agent.common.utils.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/docs/**",
            "/api/*/swagger-ui/**",
            "/api/*/v3/api-docs/**"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_AUTHORIZATION = HttpHeaders.AUTHORIZATION;

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthGlobalFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        String token = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (!StringUtils.hasText(token) || !token.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange.getResponse(), "缺少或格式错误的认证信息");
        }

        token = token.substring(BEARER_PREFIX_LENGTH);
        if (!JwtUtil.validateToken(token)) {
            return unauthorized(exchange.getResponse(), "Token 无效或已过期");
        }

        if (!JwtUtil.isAccessToken(token)) {
            return unauthorized(exchange.getResponse(), "Token 类型错误，请使用 Access Token");
        }

        Long userId = JwtUtil.getUserId(token);
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(HEADER_USER_ID, String.valueOf(userId))
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body;
        try {
            body = objectMapper.writeValueAsString(Result.error(HttpStatus.UNAUTHORIZED.value(), message));
        } catch (JsonProcessingException e) {
            log.error("序列化认证失败响应异常", e);
            body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
