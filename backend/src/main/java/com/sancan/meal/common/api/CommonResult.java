package com.sancan.meal.common.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * 统一响应体，字段与 RuoYi-Vue-Pro 的 {@code CommonResult} 保持一致：
 * {@code code} 为 0 表示成功，非 0 为业务或系统错误码。
 */
public class CommonResult<T> implements Serializable {

    private Integer code;
    private T data;
    private String msg;

    public CommonResult() {
    }

    public CommonResult(Integer code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    public static <T> CommonResult<T> ok() {
        return new CommonResult<>(0, null, "");
    }

    public static <T> CommonResult<T> ok(T data) {
        return new CommonResult<>(0, data, "");
    }

    public static <T> CommonResult<T> error(Integer code, String msg) {
        return new CommonResult<>(code, null, msg);
    }

    /** 仅供服务端判断，不参与序列化——保持与 RuoYi CommonResult 的字段完全一致。 */
    @JsonIgnore
    public boolean isSuccess() {
        return code != null && code == 0;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
