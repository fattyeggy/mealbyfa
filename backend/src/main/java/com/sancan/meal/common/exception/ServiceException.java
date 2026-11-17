package com.sancan.meal.common.exception;

/** 业务异常：携带错误码，由全局异常处理器转换为统一响应体。 */
public class ServiceException extends RuntimeException {

    private final int code;

    public ServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public ServiceException(ErrorCode errorCode, String detail) {
        super(detail == null || detail.isBlank() ? errorCode.getMessage() : detail);
        this.code = errorCode.getCode();
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ServiceException of(ErrorCode errorCode) {
        return new ServiceException(errorCode);
    }

    public static ServiceException of(ErrorCode errorCode, String detail) {
        return new ServiceException(errorCode, detail);
    }
}
