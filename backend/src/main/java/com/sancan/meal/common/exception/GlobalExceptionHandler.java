package com.sancan.meal.common.exception;

import com.sancan.meal.common.api.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。HTTP 状态统一返回 200，业务结果由响应体 code 表达，
 * 与 RuoYi-Vue-Pro 的约定一致，前端只需判断 code。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public CommonResult<Void> handleServiceException(ServiceException ex) {
        log.warn("业务异常 code={} msg={}", ex.getCode(), ex.getMessage());
        return CommonResult.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public CommonResult<Void> handleValidationException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return CommonResult.error(ErrorCode.BAD_REQUEST.getCode(), detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public CommonResult<Void> handleBadRequest(Exception ex) {
        log.warn("请求参数异常: {}", ex.getMessage());
        return CommonResult.error(ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public CommonResult<Void> handleNotFound(NoResourceFoundException ex) {
        log.warn("接口不存在 uri={}", ex.getResourcePath());
        return CommonResult.error(ErrorCode.NOT_FOUND.getCode(), "接口不存在：" + ex.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 uri={}", request.getRequestURI(), ex);
        return CommonResult.error(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }
}
