package com.xiaoai.agent.common.api;

import lombok.Getter;

@Getter
public enum ErrorCode {

    BAD_REQUEST("400", "Bad request"),
    UNAUTHORIZED("401", "Unauthorized"),
    FORBIDDEN("403", "Forbidden"),
    NOT_FOUND("404", "Resource not found"),
    BUSINESS_ERROR("1000", "Business error"),
    SYSTEM_ERROR("500", "System error");

    private final String code;

    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
