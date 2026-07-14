package com.onclick.domain.consulting.service;

import java.time.LocalDate;
import java.util.Optional;

import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.consulting.TestFieldSetter;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ConsultingRequestFactoryTest {

    private static final Long USER_ID = 7L;
    private static final Long STORE_ID = 3L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private ConsultingRequestFactory requestFactory;

    @Test
    void createsDocumentedDailyConsultingRequest() {
        User owner = new User("owner", "hash");
        TestFieldSetter.setField(owner, "id", USER_ID);
        Store store = new Store(owner, "강남점");
        TestFieldSetter.setField(store, "id", STORE_ID);
        given(storeRepository.findById(STORE_ID)).willReturn(Optional.of(store));

        var request = requestFactory.create(
                new ConsultingJobClaim(20L, STORE_ID, TARGET_DATE, 1)
        );

        assertThat(request.userId()).isEqualTo(USER_ID);
        assertThat(request.storeId()).isEqualTo(STORE_ID);
        assertThat(request.targetDate()).isEqualTo(TARGET_DATE);
        assertThat(request.reportFormat()).isEqualTo(ConsultingGenerationRequest.DAILY_V1);
    }
}
