package com.onclick.domain.visitor.service;

import com.onclick.domain.visitor.dto.HourlyVisitorResponse;
import com.onclick.domain.visitor.dto.HourlyVisitorUpsertRequest;
import com.onclick.domain.visitor.entity.HourlyVisitorCount;
import com.onclick.domain.visitor.repository.HourlyVisitorCountRepository;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HourlyVisitorServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-07-13T06:30:00Z");

    @Mock
    private HourlyVisitorCountRepository visitorCountRepository;

    private HourlyVisitorService hourlyVisitorService;

    @BeforeEach
    void setUp() {
        hourlyVisitorService = new HourlyVisitorService(
                visitorCountRepository,
                Clock.fixed(NOW, SEOUL)
        );
    }

    @Test
    void createsCurrentHourVisitorCount() {
        HourlyVisitorUpsertRequest request = new HourlyVisitorUpsertRequest(
                LocalDate.of(2026, 7, 13),
                15,
                23L
        );
        when(visitorCountRepository.findByStoreIdAndBusinessDateAndHour(1L, request.businessDate(), 15))
                .thenReturn(Optional.empty());
        when(visitorCountRepository.save(any(HourlyVisitorCount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HourlyVisitorResponse response = hourlyVisitorService.upsert(1L, SEOUL, request);

        assertThat(response.storeId()).isEqualTo(1L);
        assertThat(response.businessDate()).isEqualTo(request.businessDate());
        assertThat(response.hour()).isEqualTo(15);
        assertThat(response.visitorCount()).isEqualTo(23L);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void updatesExistingVisitorCountInsteadOfCreatingDuplicate() {
        Instant createdAt = Instant.parse("2026-07-12T03:00:00Z");
        LocalDate businessDate = LocalDate.of(2026, 7, 12);
        HourlyVisitorCount existing = new HourlyVisitorCount(1L, businessDate, 11, 8L, createdAt);
        HourlyVisitorUpsertRequest request = new HourlyVisitorUpsertRequest(businessDate, 11, 31L);
        when(visitorCountRepository.findByStoreIdAndBusinessDateAndHour(1L, businessDate, 11))
                .thenReturn(Optional.of(existing));
        when(visitorCountRepository.save(existing)).thenReturn(existing);

        HourlyVisitorResponse response = hourlyVisitorService.upsert(1L, SEOUL, request);

        assertThat(response.visitorCount()).isEqualTo(31L);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(visitorCountRepository).save(existing);
    }

    @Test
    void rejectsFutureHourInSeoul() {
        HourlyVisitorUpsertRequest request = new HourlyVisitorUpsertRequest(
                LocalDate.of(2026, 7, 13),
                16,
                10L
        );

        assertThatThrownBy(() -> hourlyVisitorService.upsert(1L, SEOUL, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.FUTURE_TIME_NOT_ALLOWED);
        verify(visitorCountRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidHourAndNegativeCountAtServiceBoundary() {
        HourlyVisitorUpsertRequest invalidHour = new HourlyVisitorUpsertRequest(
                LocalDate.of(2026, 7, 13),
                24,
                1L
        );
        HourlyVisitorUpsertRequest negativeCount = new HourlyVisitorUpsertRequest(
                LocalDate.of(2026, 7, 13),
                14,
                -1L
        );

        assertThatThrownBy(() -> hourlyVisitorService.upsert(1L, SEOUL, invalidHour))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_VISITOR_INPUT);
        assertThatThrownBy(() -> hourlyVisitorService.upsert(1L, SEOUL, negativeCount))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_VISITOR_INPUT);
        verify(visitorCountRepository, never()).save(any());
    }

    @Test
    void usesStoreTimeZoneWhenDeterminingWhetherHourIsFuture() {
        ZoneId newYork = ZoneId.of("America/New_York");
        HourlyVisitorUpsertRequest request = new HourlyVisitorUpsertRequest(
                LocalDate.of(2026, 7, 13),
                3,
                5L
        );

        assertThatThrownBy(() -> hourlyVisitorService.upsert(1L, newYork, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.FUTURE_TIME_NOT_ALLOWED);
        verify(visitorCountRepository, never()).save(any());
    }
}
