package com.onclick.domain.store.service;

import java.time.LocalTime;

import com.onclick.domain.store.entity.Industry;
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

    public LocalTime normalizeClosingTime(LocalTime closingTime) {
        return closingTime == null ? Store.DEFAULT_CLOSING_TIME : closingTime;
    }

    public Industry normalizeIndustry(Industry industry) {
        return industry == null ? Store.DEFAULT_INDUSTRY : industry;
    }

    public String normalizeRoadAddress(String roadAddress) {
        return roadAddress == null ? null : requireRoadAddress(roadAddress);
    }

    public String requireRoadAddress(String roadAddress) {
        if (roadAddress == null || roadAddress.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "매장 주소는 비어 있을 수 없습니다.");
        }
        return roadAddress.trim();
    }
}
