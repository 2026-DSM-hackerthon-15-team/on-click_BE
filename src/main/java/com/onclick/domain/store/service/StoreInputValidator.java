package com.onclick.domain.store.service;

import java.time.DateTimeException;
import java.time.ZoneId;

import com.onclick.domain.store.entity.Store;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class StoreInputValidator {

    public String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지점 이름은 비어 있을 수 없습니다.");
        }
        return name.trim();
    }

    public String normalizeTimeZone(String timeZone) {
        return requireTimeZone(timeZone == null || timeZone.isBlank() ? Store.DEFAULT_TIME_ZONE : timeZone);
    }

    public String requireTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지점 시간대는 비어 있을 수 없습니다.");
        }
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (DateTimeException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "지원하지 않는 지점 시간대입니다.");
        }
    }
}
