package com.agent.common.result;

import com.agent.common.exception.ErrorCode;
import lombok.Data;

import java.time.Instant;

@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;

    public Result() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(ErrorCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(ErrorCode.SYSTEM_ERROR.getCode(), message);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }
}
