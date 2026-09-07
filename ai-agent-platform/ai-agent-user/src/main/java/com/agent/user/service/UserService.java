package com.agent.user.service;

import com.agent.user.dto.LoginRequest;
import com.agent.user.dto.RegisterRequest;
import com.agent.user.dto.TokenResponse;
import com.agent.user.vo.UserVO;

public interface UserService {

    UserVO register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refreshToken(String refreshToken);

    UserVO getUserProfile(Long currentUserId, Long userId);

    enum UserStatus {
        DISABLED(0),
        ENABLED(1),
        LOCKED(2);

        private final int value;

        UserStatus(int value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }
}
