package com.agent.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    @DisplayName("枚举值与 code/message 对应正确")
    void shouldHaveCorrectCodeAndMessage() {
        assertThat(ErrorCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(ErrorCode.SUCCESS.getMessage()).isEqualTo("success");

        assertThat(ErrorCode.PARAM_ERROR.getCode()).isEqualTo(400);
        assertThat(ErrorCode.PARAM_ERROR.getMessage()).isEqualTo("参数错误");

        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(403);
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(ErrorCode.SYSTEM_ERROR.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("每个错误码 message 非空")
    void shouldHaveNonNullMessage() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getMessage()).isNotBlank();
            assertThat(errorCode.getCode()).isPositive();
        }
    }
}
