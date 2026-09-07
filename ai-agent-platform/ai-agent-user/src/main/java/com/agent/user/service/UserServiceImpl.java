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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 86400L;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserVO register(RegisterRequest request) {
        SysUser existing = userMapper.selectByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ENABLED.getValue());

        userMapper.insert(user);

        return convertToVO(user);
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        SysUser user = userMapper.selectByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }

        if (!UserStatus.ENABLED.getValue().equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }

        String accessToken = JwtUtil.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = JwtUtil.generateRefreshToken(user.getId());

        return buildTokenResponse(accessToken, refreshToken);
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        if (!JwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "Refresh Token 无效");
        }

        if (!JwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "Token 类型错误");
        }

        Long userId = JwtUtil.getUserId(refreshToken);
        SysUser user = userMapper.selectById(userId);
        if (user == null || !UserStatus.ENABLED.getValue().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "用户不存在或已被禁用");
        }

        String newAccessToken = JwtUtil.generateAccessToken(user.getId(), user.getUsername());
        String newRefreshToken = JwtUtil.generateRefreshToken(user.getId());

        return buildTokenResponse(newAccessToken, newRefreshToken);
    }

    @Override
    public UserVO getUserProfile(Long currentUserId, Long userId) {
        // 越权校验：仅允许查询本人资料，管理员可通过上层权限放行后直接传入相同 ID
        if (currentUserId == null || !currentUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该用户资料");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "用户不存在");
        }

        return convertToVO(user);
    }

    private TokenResponse buildTokenResponse(String accessToken, String refreshToken) {
        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(ACCESS_TOKEN_EXPIRES_IN_SECONDS);
        response.setTokenType(TOKEN_TYPE_BEARER);
        return response;
    }

    private UserVO convertToVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
