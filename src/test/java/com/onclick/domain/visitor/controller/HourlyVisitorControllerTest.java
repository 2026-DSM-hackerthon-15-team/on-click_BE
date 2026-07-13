package com.onclick.domain.visitor.controller;

import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.entity.UserStoreMembership;
import com.onclick.domain.visitor.dto.HourlyVisitorResponse;
import com.onclick.domain.visitor.dto.HourlyVisitorUpsertRequest;
import com.onclick.domain.visitor.service.HourlyVisitorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HourlyVisitorControllerTest {

    @Mock
    private HourlyVisitorService hourlyVisitorService;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private UserStoreMembership membership;

    @Mock
    private Store store;

    @Test
    void validatesStoreMembershipBeforeUpserting() {
        HourlyVisitorController controller = new HourlyVisitorController(
                hourlyVisitorService,
                storeAccessValidator
        );
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("7")
                .issuedAt(Instant.parse("2026-07-13T06:00:00Z"))
                .expiresAt(Instant.parse("2026-07-13T07:00:00Z"))
                .build();
        HourlyVisitorUpsertRequest request = new HourlyVisitorUpsertRequest(
                LocalDate.of(2026, 7, 13),
                14,
                20L
        );
        HourlyVisitorResponse expected = new HourlyVisitorResponse(
                1L,
                3L,
                request.businessDate(),
                14,
                20L,
                Instant.parse("2026-07-13T06:00:00Z"),
                Instant.parse("2026-07-13T06:00:00Z")
        );
        ZoneId storeZoneId = ZoneId.of("Asia/Seoul");
        when(storeAccessValidator.validate(jwt, 3L)).thenReturn(membership);
        when(membership.getStore()).thenReturn(store);
        when(store.zoneId()).thenReturn(storeZoneId);
        when(hourlyVisitorService.upsert(3L, storeZoneId, request)).thenReturn(expected);

        HourlyVisitorResponse response = controller.upsertHourlyVisitorCount(jwt, 3L, request);

        assertThat(response).isEqualTo(expected);
        verify(storeAccessValidator).validate(jwt, 3L);
        verify(hourlyVisitorService).upsert(3L, storeZoneId, request);
    }
}
