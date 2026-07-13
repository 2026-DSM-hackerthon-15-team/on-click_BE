package com.onclick.domain.visitor.service;

import com.onclick.domain.visitor.dto.HourlyVisitorResponse;
import com.onclick.domain.visitor.dto.HourlyVisitorUpsertRequest;
import com.onclick.domain.visitor.entity.HourlyVisitorCount;
import com.onclick.domain.visitor.repository.HourlyVisitorCountRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class HourlyVisitorService {

    private final HourlyVisitorCountRepository visitorCountRepository;
    private final Clock clock;

    public HourlyVisitorService(HourlyVisitorCountRepository visitorCountRepository, Clock clock) {
        this.visitorCountRepository = visitorCountRepository;
        this.clock = clock;
    }

    @Transactional
    public HourlyVisitorResponse upsert(
            Long storeId,
            ZoneId storeZoneId,
            HourlyVisitorUpsertRequest request
    ) {
        validateInput(storeId, request);
        Objects.requireNonNull(storeZoneId, "storeZoneId must not be null");
        Instant now = clock.instant();
        Instant bucketStart = request.businessDate()
                .atTime(request.hour(), 0)
                .atZone(storeZoneId)
                .toInstant();

        if (bucketStart.isAfter(now)) {
            throw new ApiException(ErrorCode.FUTURE_TIME_NOT_ALLOWED);
        }

        HourlyVisitorCount visitorCount = visitorCountRepository
                .findByStoreIdAndBusinessDateAndHour(storeId, request.businessDate(), request.hour())
                .map(existing -> {
                    existing.updateVisitorCount(request.visitorCount(), now);
                    return existing;
                })
                .orElseGet(() -> new HourlyVisitorCount(
                        storeId,
                        request.businessDate(),
                        request.hour(),
                        request.visitorCount(),
                        now
                ));

        return HourlyVisitorResponse.from(visitorCountRepository.save(visitorCount));
    }

    private void validateInput(Long storeId, HourlyVisitorUpsertRequest request) {
        if (storeId == null || storeId <= 0 || request == null
                || request.businessDate() == null
                || request.hour() == null || request.hour() < 0 || request.hour() > 23
                || request.visitorCount() == null || request.visitorCount() < 0) {
            throw new ApiException(ErrorCode.INVALID_VISITOR_INPUT);
        }
    }
}
