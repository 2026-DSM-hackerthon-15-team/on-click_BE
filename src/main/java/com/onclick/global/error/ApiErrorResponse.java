package com.onclick.global.error;

public record ApiErrorResponse(String errorCode, String message) {

    public static ApiErrorResponse from(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.name(), errorCode.defaultMessage());
    }

    public static ApiErrorResponse from(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(errorCode.name(), message);
    }
}
