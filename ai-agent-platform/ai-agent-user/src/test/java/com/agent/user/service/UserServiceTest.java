package com.agent.user.service;

import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import com.agent.common.utils.JwtUtil;
import com.agent.user.dto.LoginRequest;
import com.agent.user.dto.RegisterRequest;
import com.agent.user.dto.TokenResponse;
import com.agent.user.entity.SysUser;
import com.agent.user.mapper.SysUserMapper;
import com.agent.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = "jwt.secret=TestSecretKeyForAiAgentPlatform2026!@#$")
class UserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "TestSecretKeyForAiAgentPlatform2026!@#$");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpire", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpire", 604800000L);
        jwtUtil.init();
    }

    @Test
    @DisplayName("注册成功")
    void shouldRegisterSuccessfully() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("newuser@example.com");
        request.setPhone("13800138000");

        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        UserVO userVO = userService.register(request);

        assertThat(userVO).isNotNull();
        assertThat(userVO.getUsername()).isEqualTo("newuser");
        assertThat(userVO.getEmail()).isEqualTo("newuser@example.com");
        verify(userMapper, times(1)).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("注册时用户名已存在应抛异常")
    void shouldThrowExceptionWhenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");
        request.setPassword("Password123");

        when(userMapper.selectByUsername("existinguser")).thenReturn(new SysUser());

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    @DisplayName("登录成功返回 Token")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("Password123");

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encodedPassword");
        user.setStatus(UserService.UserStatus.ENABLED.getValue());

        when(userMapper.selectByUsername("testuser")).thenReturn(user);
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        TokenResponse tokenResponse = userService.login(request);

        assertThat(tokenResponse).isNotNull();
        assertThat(tokenResponse.getAccessToken()).isNotBlank();
        assertThat(tokenResponse.getRefreshToken()).isNotBlank();
        assertThat(tokenResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(tokenResponse.getExpiresIn()).isEqualTo(86400L);
    }

    @Test
    @DisplayName("登录时密码错误应抛异常")
    void shouldThrowExceptionWhenPasswordMismatch() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("WrongPassword");

        SysUser user = new SysUser();
        user.setUsername("testuser");
        user.setPassword("encodedPassword");
        user.setStatus(UserService.UserStatus.ENABLED.getValue());

        when(userMapper.selectByUsername("testuser")).thenReturn(user);
        when(passwordEncoder.matches("WrongPassword", "encodedPassword")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
                })
                .hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("登录时账号被禁用应抛异常")
    void shouldThrowExceptionWhenAccountDisabled() {
        LoginRequest request = new LoginRequest();
        request.setUsername("disableduser");
        request.setPassword("Password123");

        SysUser user = new SysUser();
        user.setUsername("disableduser");
        user.setPassword("encodedPassword");
        user.setStatus(UserService.UserStatus.DISABLED.getValue());

        when(userMapper.selectByUsername("disableduser")).thenReturn(user);
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已被禁用");
    }

    @Test
    @DisplayName("刷新 Token 成功")
    void shouldRefreshTokenSuccessfully() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("testuser");
        user.setStatus(UserService.UserStatus.ENABLED.getValue());

        String refreshToken = JwtUtil.generateRefreshToken(1L);

        when(userMapper.selectById(1L)).thenReturn(user);

        TokenResponse tokenResponse = userService.refreshToken(refreshToken);

        assertThat(tokenResponse).isNotNull();
        assertThat(tokenResponse.getAccessToken()).isNotBlank();
        assertThat(tokenResponse.getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("刷新 Token 无效应抛异常")
    void shouldThrowExceptionWhenRefreshTokenInvalid() {
        assertThatThrownBy(() -> userService.refreshToken("invalid.token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
                })
                .hasMessage("Refresh Token 无效");
    }

    @Test
    @DisplayName("查询用户资料成功")
    void shouldGetUserProfile() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setStatus(UserService.UserStatus.ENABLED.getValue());

        when(userMapper.selectById(1L)).thenReturn(user);

        UserVO userVO = userService.getUserProfile(1L);

        assertThat(userVO).isNotNull();
        assertThat(userVO.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("查询用户不存在应抛异常")
    void shouldThrowExceptionWhenUserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getUserProfile(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
                })
                .hasMessage("用户不存在");
    }
}
