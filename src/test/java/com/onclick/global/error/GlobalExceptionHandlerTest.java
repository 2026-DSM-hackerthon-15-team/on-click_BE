package com.onclick.global.error;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void usesStatusAndMessageFromApplicationException() {
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(
                new ApiException(ErrorCode.FUTURE_DATE_NOT_ALLOWED));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "FUTURE_DATE_NOT_ALLOWED", "미래 영업일은 조회할 수 없습니다."));
    }

    @Test
    void mapsInvalidDateTypeToBadRequest() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "2026-13-40", LocalDate.class, "date", null, new IllegalArgumentException("invalid date"));

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "INVALID_DATE", "날짜 형식이 올바르지 않습니다."));
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("sensitive implementation detail"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
