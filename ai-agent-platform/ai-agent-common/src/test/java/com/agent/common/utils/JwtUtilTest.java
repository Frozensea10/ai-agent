package com.agent.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JwtUtil.class)
@TestPropertySource(properties = {
        "jwt.secret=TestSecretKeyForAiAgentPlatform2026!@#$",
        "jwt.access-token-expire=86400000",
        "jwt.refresh-token-expire=604800000"
})
class JwtUtilTest {

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "testuser";

    @Test
    @DisplayName("生成并验证 accessToken 成功")
    void shouldGenerateAndValidateAccessToken() {
        String accessToken = JwtUtil.generateAccessToken(USER_ID, USERNAME);

        assertThat(accessToken).isNotBlank();
        assertThat(JwtUtil.validateToken(accessToken)).isTrue();
        assertThat(JwtUtil.isAccessToken(accessToken)).isTrue();
        assertThat(JwtUtil.isRefreshToken(accessToken)).isFalse();
        assertThat(JwtUtil.getUserId(accessToken)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("生成并验证 refreshToken 成功")
    void shouldGenerateAndValidateRefreshToken() {
        String refreshToken = JwtUtil.generateRefreshToken(USER_ID);

        assertThat(refreshToken).isNotBlank();
        assertThat(JwtUtil.validateToken(refreshToken)).isTrue();
        assertThat(JwtUtil.isRefreshToken(refreshToken)).isTrue();
        assertThat(JwtUtil.isAccessToken(refreshToken)).isFalse();
        assertThat(JwtUtil.getUserId(refreshToken)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("验证非法 token 应返回 false")
    void shouldReturnFalseForInvalidToken() {
        assertThat(JwtUtil.validateToken("invalid.token.value")).isFalse();
        assertThat(JwtUtil.validateToken("")).isFalse();
        assertThat(JwtUtil.validateToken(null)).isFalse();
    }
}
