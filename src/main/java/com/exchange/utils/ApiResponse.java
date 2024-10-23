package com.exchange.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // 確保只包含非空字段
public class ApiResponse<T> {
    private String message;
    private T data;
    private String errorCode; // 錯誤碼

    // 成功回應
    public ApiResponse(String message, T data) {
        this.message = message;
        this.data = data;
        this.errorCode = null; // 成功回應不需要 errorCode
    }

    public ApiResponse(String message, Integer errorCode) {
        this.message = message;
        this.errorCode = errorCode.toString(); // 錯誤回應需要 errorCode
        this.data = null;
    }
}
