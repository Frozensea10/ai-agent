package com.agent.user.config;

import com.agent.common.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GatewayUserIdFilterTest {

    @Mock
    private FilterChain filterChain;

    private GatewayUserIdFilter filter;

    @BeforeAll
    static void initJwt() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "TestSecretKeyForAiAgentPlatform2026!@#$");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpire", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpire", 604800000L);
        jwtUtil.init();
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new GatewayUserIdFilter();
    }

    @AfterAll
    static void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无 Authorization 头时不设置认证，过滤器链继续执行")
    void doFilter_noAuthHeader_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 非 Bearer 前缀时不设置认证")
    void doFilter_nonBearerHeader_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer token 无效（垃圾字符串）时不设置认证，过滤器链继续执行")
    void doFilter_invalidToken_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer token 为 refresh token（非 access）时不设置认证")
    void doFilter_refreshToken_shouldNotSetAuthentication() throws Exception {
        String refreshToken = JwtUtil.generateRefreshToken(1L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("有效 access token 设置认证并写入 userId 到 details")
    void doFilter_validAccessToken_shouldSetAuthentication() throws Exception {
        Long userId = 42L;
        String accessToken = JwtUtil.generateAccessToken(userId, "alice");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getDetails()).isEqualTo(userId);
        assertThat(auth.getName()).isEqualTo(String.valueOf(userId));
        assertThat(auth.getAuthorities())
                .anySatisfy(a -> assertThat(a.getAuthority()).isEqualTo("ROLE_USER"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("SecurityContext 已有认证时不覆盖现有认证")
    void doFilter_existingAuthentication_shouldNotOverwrite() throws Exception {
        var existingAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "existing-user", null,
                java.util.Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        String accessToken = JwtUtil.generateAccessToken(99L, "new-user");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isSameAs(existingAuth);
        assertThat(auth.getName()).isEqualTo("existing-user");
    }
}
