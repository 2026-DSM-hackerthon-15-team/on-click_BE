package com.onclick.global.error;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.http.HttpMethod.GET;

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
    void mapsAuthenticationFailureToUnauthorized() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAuthenticationException(
                new BadCredentialsException("sensitive authentication detail"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "UNAUTHORIZED", "인증이 필요합니다."));
    }

    @Test
    void mapsAccessDenialToForbidden() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAccessDeniedException(
                new AccessDeniedException("sensitive authorization detail"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "FORBIDDEN", "접근 권한이 없습니다."));
    }

    @Test
    void mapsUnknownResourceToNotFound() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(
                new NoResourceFoundException(GET, "/missing"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    void mapsUnsupportedMethodAndPreservesAllowHeader() {
        ResponseEntity<ApiErrorResponse> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getAllow()).containsExactly(org.springframework.http.HttpMethod.POST);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."));
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
