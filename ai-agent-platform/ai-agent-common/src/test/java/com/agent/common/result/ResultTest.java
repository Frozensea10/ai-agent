package com.agent.common.result;

import com.agent.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    @DisplayName("构造成功响应")
    void shouldBuildSuccessResult() {
        String data = "hello";
        Result<String> result = Result.success(data);

        assertThat(result.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(result.getMessage()).isEqualTo(ErrorCode.SUCCESS.getMessage());
        assertThat(result.getData()).isEqualTo(data);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("构造无数据成功响应")
    void shouldBuildSuccessResultWithoutData() {
        Result<Void> result = Result.success();

        assertThat(result.getCode()).isEqualTo(ErrorCode.SUCCESS.getCode());
        assertThat(result.getData()).isNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("构造错误响应")
    void shouldBuildErrorResult() {
        Result<Void> result = Result.error(ErrorCode.PARAM_ERROR);

        assertThat(result.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(ErrorCode.PARAM_ERROR.getMessage());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("构造自定义消息错误响应")
    void shouldBuildErrorResultWithCustomMessage() {
        String message = "用户名不能为空";
        Result<Void> result = Result.error(message);

        assertThat(result.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("构造指定 code 错误响应")
    void shouldBuildErrorResultWithCode() {
        Result<Void> result = Result.error(400, "参数校验失败");

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("参数校验失败");
        assertThat(result.isSuccess()).isFalse();
    }
}
