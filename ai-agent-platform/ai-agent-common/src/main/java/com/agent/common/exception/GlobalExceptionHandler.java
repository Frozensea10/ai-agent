package com.agent.common.exception;

import com.agent.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传大小超过限制: {}", e.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "文件大小超过限制，最大支持 50MB");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (e.getCause() != null) {
            detail += " | cause: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
        }
        return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统繁忙: " + detail);
    }

    @ExceptionHandler(Throwable.class)
    public Result<Void> handleThrowable(Throwable e) {
        log.error("系统严重异常", e);
        String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (e.getCause() != null) {
            detail += " | cause: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
        }
        return Result.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误: " + detail);
    }
}
