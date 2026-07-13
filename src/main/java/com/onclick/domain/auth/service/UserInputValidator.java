package com.onclick.domain.auth.service;

import java.util.Locale;
import java.util.regex.Pattern;

import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class UserInputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public String requireAccountId(String accountId) {
        String normalized = requireText(accountId, "로그인 아이디", 50);
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw invalid("로그인 아이디에는 공백을 사용할 수 없습니다.");
        }
        return normalized;
    }

    public String requireName(String name) {
        return requireText(name, "이름", 100);
    }

    public String requireEmail(String email) {
        String normalized = requireText(email, "이메일", 255).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw invalid("이메일 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid(fieldName + "은(는) " + maxLength + "자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message);
    }
}
