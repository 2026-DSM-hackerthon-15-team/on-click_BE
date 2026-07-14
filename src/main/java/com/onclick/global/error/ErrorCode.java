package com.onclick.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_DATE(HttpStatus.BAD_REQUEST, "날짜 형식이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    STORE_CONTEXT_MISSING(HttpStatus.FORBIDDEN, "접근 가능한 매장 정보를 확인할 수 없습니다."),
    LOGIN_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 로그인 아이디입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
    STORE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 매장에 접근할 권한이 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    SALE_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "판매 거래를 찾을 수 없습니다."),
    SALE_TRANSACTION_CONFLICT(HttpStatus.CONFLICT, "같은 거래번호로 다른 판매 내용이 등록되어 있습니다."),
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_MESSAGE_CONFLICT(HttpStatus.CONFLICT, "같은 메시지 번호로 다른 내용이 등록되어 있습니다."),
    MARKETING_NOT_FOUND(HttpStatus.NOT_FOUND, "마케팅 콘텐츠를 찾을 수 없습니다."),
    MARKETING_STATUS_CONFLICT(HttpStatus.CONFLICT, "현재 상태에서는 마케팅 콘텐츠를 변경할 수 없습니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    INVALID_MEDIA_FILE(HttpStatus.BAD_REQUEST, "이미지 파일이 올바르지 않습니다."),
    MEDIA_IN_USE(HttpStatus.CONFLICT, "마케팅 콘텐츠에서 사용 중인 이미지는 삭제할 수 없습니다."),
    MEDIA_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장할 수 없습니다."),
    CONSULTING_NOT_FOUND(HttpStatus.NOT_FOUND, "컨설팅 결과를 찾을 수 없습니다."),
    LEGAL_ADVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "법률 조언을 찾을 수 없습니다."),
    INSTAGRAM_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Instagram 계정 정보를 찾을 수 없습니다."),
    AUTOMATION_NOT_FOUND(HttpStatus.NOT_FOUND, "자동화 설정을 찾을 수 없습니다."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서비스를 일시적으로 사용할 수 없습니다."),
    AI_REQUEST_REJECTED(HttpStatus.BAD_GATEWAY, "AI 서비스가 요청을 처리하지 못했습니다."),
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI 서비스 응답 형식이 올바르지 않습니다."),
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

    public String defaultMessage() {
        return defaultMessage;
    }
}
