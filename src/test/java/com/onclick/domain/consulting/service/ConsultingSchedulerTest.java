package com.onclick.domain.consulting.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.onclick.domain.auth.entity.User;
import com.onclick.domain.consulting.TestFieldSetter;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultingSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 22, 5);
    private static final Long STORE_ID = 3L;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ConsultingJobManager jobManager;

    @Mock
    private ConsultingJobProcessor jobProcessor;

    private ConsultingScheduler scheduler;

    @BeforeEach
    void setUp() {
        ConsultingSchedulerProperties properties = new ConsultingSchedulerProperties();
        properties.setMaxAttempts(3);
        properties.setBatchSize(100);
        properties.setRetryDelay(Duration.ofMinutes(5));
        properties.setLeaseDuration(Duration.ofMinutes(2));
        scheduler = new ConsultingScheduler(
                storeRepository,
                jobManager,
                jobProcessor,
                properties,
                fixedClock(NOW)
        );
    }

    @Test
    void createsCurrentBusinessDateAfterClosingAndUsesSharedProcessor() {
        Store store = store();
        given(storeRepository.findAll()).willReturn(List.of(store));
        given(jobManager.findRetryableIds(NOW, 100)).willReturn(List.of(10L));

        scheduler.generateDueConsultings();

        verify(jobManager).createPending(STORE_ID, LocalDate.of(2026, 7, 13), NOW);
        verify(jobProcessor).process(10L);
    }

    private Store store() {
        Store store = new Store(new User("owner", "hash"), "강남점");
        TestFieldSetter.setField(store, "id", STORE_ID);
        TestFieldSetter.setField(
                store,
                "createdAt",
                LocalDateTime.of(2026, 7, 1, 9, 0)
        );
        return store;
    }

    private Clock fixedClock(LocalDateTime localDateTime) {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return Clock.fixed(localDateTime.atZone(kst).toInstant(), kst);
    }
}
