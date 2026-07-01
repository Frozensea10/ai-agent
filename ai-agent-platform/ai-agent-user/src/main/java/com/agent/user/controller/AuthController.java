package com.agent.user.controller;

import com.agent.common.result.Result;
import com.agent.user.dto.LoginRequest;
import com.agent.user.dto.RegisterRequest;
import com.agent.user.dto.TokenResponse;
import com.agent.user.service.UserService;
import com.agent.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/auth/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/auth/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/auth/refresh")
    public Result<TokenResponse> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken) {
        return Result.success(userService.refreshToken(refreshToken));
    }

    @GetMapping("/user/profile")
    public Result<UserVO> getProfile(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getUserProfile(userId));
    }
}
