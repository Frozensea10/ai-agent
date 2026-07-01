package com.agent.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    @DisplayName("根据 ErrorCode 构造业务异常")
    void shouldConstructWithErrorCode() {
        BusinessException exception = new BusinessException(ErrorCode.PARAM_ERROR);

        assertThat(exception.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.PARAM_ERROR.getMessage());
    }

    @Test
    @DisplayName("根据自定义消息构造业务异常")
    void shouldConstructWithCustomMessage() {
        String customMessage = "用户名已存在";
        BusinessException exception = new BusinessException(customMessage);

        assertThat(exception.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(exception.getMessage()).isEqualTo(customMessage);
    }

    @Test
    @DisplayName("根据 ErrorCode 和自定义消息构造业务异常")
    void shouldConstructWithErrorCodeAndMessage() {
        BusinessException exception = new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");

        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(exception.getMessage()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("业务异常支持异常链")
    void shouldSupportCause() {
        Throwable cause = new RuntimeException("原始异常");
        BusinessException exception = new BusinessException(ErrorCode.SYSTEM_ERROR, "系统异常", cause);

        assertThat(exception.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(exception.getCause()).isEqualTo(cause);
    }
}
