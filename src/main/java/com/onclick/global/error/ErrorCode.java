package com.onclick.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_DATE(HttpStatus.BAD_REQUEST, "날짜 형식이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    STORE_CONTEXT_MISSING(HttpStatus.FORBIDDEN, "접근 가능한 매장 정보를 확인할 수 없습니다."),
    LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 로그인 아이디입니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
    STORE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 매장에 접근할 권한이 없습니다."),
    STORE_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "매장 소유자 권한이 필요합니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    SALE_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "판매 거래를 찾을 수 없습니다."),
    SALE_TRANSACTION_CONFLICT(HttpStatus.CONFLICT, "같은 거래번호로 다른 판매 내용이 등록되어 있습니다."),
    INVALID_VISITOR_INPUT(HttpStatus.BAD_REQUEST, "방문자 입력 값이 올바르지 않습니다."),
    FUTURE_TIME_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "미래 시간의 방문자 수는 등록할 수 없습니다."),
    FUTURE_DATE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "미래 영업일은 조회할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
