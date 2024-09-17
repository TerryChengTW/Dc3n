package com.exchange.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL) // 只包含非空字段
public class ApiResponse<T> {
    private String message;
    private T data;
    private String errorCode; // 錯誤碼

    // 成功回應
    public ApiResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

    // 錯誤回應
    public ApiResponse(String message, String errorCode) {
        this.message = message;
        this.errorCode = errorCode;
    }

}
