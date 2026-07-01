package com.agent.user.controller;

import com.agent.common.utils.JwtUtil;
import com.agent.user.config.SecurityConfig;
import com.agent.user.dto.LoginRequest;
import com.agent.user.dto.RegisterRequest;
import com.agent.user.dto.TokenResponse;
import com.agent.user.service.UserService;
import com.agent.user.vo.UserVO;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerTest.TestDataConfig.class})
@TestPropertySource(properties = "jwt.secret=TestSecretKeyForAiAgentPlatform2026!@#$")
class AuthControllerTest {

    @TestConfiguration
    static class TestDataConfig {

        @Bean
        public DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        public MetaObjectHandler metaObjectHandler() {
            return mock(MetaObjectHandler.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
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
    @DisplayName("注册接口返回成功")
    void shouldRegisterSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Password123");
        request.setEmail("newuser@example.com");

        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUsername("newuser");
        userVO.setEmail("newuser@example.com");

        when(userService.register(any(RegisterRequest.class))).thenReturn(userVO);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("newuser"));
    }

    @Test
    @DisplayName("登录接口返回 Token")
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("Password123");

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("mockAccessToken");
        tokenResponse.setRefreshToken("mockRefreshToken");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(86400L);

        when(userService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("mockAccessToken"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("登录接口参数校验失败返回业务码 400")
    void shouldReturnBadRequestWhenLoginParamsInvalid() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("ab");
        request.setPassword("123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("查询用户资料接口成功")
    void shouldGetProfileSuccessfully() throws Exception {
        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUsername("testuser");
        userVO.setEmail("test@example.com");

        when(userService.getUserProfile(1L)).thenReturn(userVO);

        mockMvc.perform(get("/api/v1/user/profile")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }
}
