package com.agent.gateway.filter;

import com.agent.common.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthGlobalFilterTest {

    private static final String SECRET = "TestSecretKeyForAiAgentPlatform2026!@#$";
    private static final Long USER_ID = 1L;
    private static final String USERNAME = "testuser";

    private AuthGlobalFilter filter;
    private ObjectMapper objectMapper;

    @Mock
    private ServerWebExchange exchange;
    @Mock
    private GatewayFilterChain chain;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpire", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpire", 604800000L);
        jwtUtil.init();

        objectMapper = new ObjectMapper();
        filter = new AuthGlobalFilter(jwtUtil, objectMapper);
    }

    private void mockExchange(String path) {
        when(exchange.getRequest()).thenReturn(request);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080" + path));
        when(exchange.getResponse()).thenReturn(response);
        lenient().when(response.getHeaders()).thenReturn(new HttpHeaders());
        lenient().when(response.bufferFactory()).thenReturn(new org.springframework.core.io.buffer.DefaultDataBufferFactory());
        lenient().when(response.writeWith(any())).thenReturn(Mono.empty());
    }

    private HttpHeaders mockRequestWithHeader(String headerName, String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        if (headerName != null && headerValue != null) {
            headers.add(headerName, headerValue);
        }
        when(request.getHeaders()).thenReturn(headers);
        return headers;
    }

    private void mockRequestMutation() {
        ServerHttpRequest.Builder requestBuilder = mock(ServerHttpRequest.Builder.class);
        ServerHttpRequest mutatedRequest = mock(ServerHttpRequest.class);
        when(request.mutate()).thenReturn(requestBuilder);
        when(requestBuilder.header(any(), any())).thenReturn(requestBuilder);
        when(requestBuilder.build()).thenReturn(mutatedRequest);
    }

    private void mockExchangeMutation() {
        ServerWebExchange.Builder exchangeBuilder = mock(ServerWebExchange.Builder.class);
        ServerWebExchange mutatedExchange = mock(ServerWebExchange.class);
        when(exchange.mutate()).thenReturn(exchangeBuilder);
        when(exchangeBuilder.request(any(ServerHttpRequest.class))).thenReturn(exchangeBuilder);
        when(exchangeBuilder.build()).thenReturn(mutatedExchange);
        when(chain.filter(mutatedExchange)).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("白名单路径直接放行")
    void shouldPassThroughWhiteListPath() {
        mockExchange("/api/v1/auth/login");
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();

        verify(chain).filter(exchange);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    @DisplayName("非白名单路径缺少 Token 返回 401")
    void shouldReturnUnauthorizedWhenTokenMissing() {
        mockExchange("/api/v1/user/profile");
        mockRequestWithHeader(null, null);
        when(response.writeWith(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(exchange);
    }

    @Test
    @DisplayName("无效 Token 返回 401")
    void shouldReturnUnauthorizedWhenTokenInvalid() {
        mockExchange("/api/v1/user/profile");
        mockRequestWithHeader(HttpHeaders.AUTHORIZATION, "Bearer invalidToken");
        when(response.writeWith(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();

        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(exchange);
    }

    @Test
    @DisplayName("有效 Token 透传 X-User-Id 并放行")
    void shouldPassThroughWithUserIdHeader() {
        mockExchange("/api/v1/user/profile");
        String token = JwtUtil.generateAccessToken(USER_ID, USERNAME);
        mockRequestWithHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        mockRequestMutation();
        mockExchangeMutation();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectComplete()
                .verify();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Filter 顺序为 -100")
    void shouldReturnOrder() {
        assertEquals(-100, filter.getOrder());
    }
}
