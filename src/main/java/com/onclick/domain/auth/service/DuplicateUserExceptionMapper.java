package com.onclick.domain.auth.service;

import java.util.Locale;

import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.dao.DataIntegrityViolationException;

final class DuplicateUserExceptionMapper {

    private DuplicateUserExceptionMapper() {
    }

    static ApiException map(DataIntegrityViolationException cause, ErrorCode fallback) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                if (constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains("email")) {
                    return exception(ErrorCode.EMAIL_ALREADY_EXISTS, cause);
                }
                if (constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains("account_id")) {
                    return exception(ErrorCode.LOGIN_ID_ALREADY_EXISTS, cause);
                }
            }
            current = current.getCause();
        }
        return exception(fallback, cause);
    }

    private static ApiException exception(ErrorCode errorCode, Throwable cause) {
        return new ApiException(errorCode, errorCode.defaultMessage(), cause);
    }
}
