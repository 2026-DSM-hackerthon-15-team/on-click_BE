package com.onclick.domain.consulting.entity;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.onclick.common.ai.dto.ConsultingGenerationResult;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 22, 0);

    @Test
    void pendingJobUsesLeaseAndCompletesWithGeneratedContent() {
        Consulting consulting = Consulting.pending(3L, LocalDate.of(2026, 7, 13), NOW);

        assertThat(consulting.claim(NOW, Duration.ofMinutes(2), 3)).isTrue();
        assertThat(consulting.claim(NOW.plusSeconds(30), Duration.ofMinutes(2), 3)).isFalse();
        consulting.complete(
                new ConsultingGenerationResult("매출 분석", "저녁 판매를 강화하세요."),
                NOW.plusSeconds(10),
                1
        );

        assertThat(consulting.getStatus()).isEqualTo(ConsultingStatus.COMPLETED);
        assertThat(consulting.getAttemptCount()).isEqualTo(1);
        assertThat(consulting.getTitle()).isEqualTo("매출 분석");
        assertThat(consulting.getContent()).isEqualTo("저녁 판매를 강화하세요.");
        assertThat(consulting.getNextRetryAt()).isNull();
    }

    @Test
    void failureIsRetryableUntilPersistedAttemptLimitIsReached() {
        Consulting consulting = Consulting.pending(3L, LocalDate.of(2026, 7, 13), NOW);

        assertThat(consulting.claim(NOW, Duration.ofMinutes(2), 2)).isTrue();
        consulting.fail("temporary", NOW, NOW.plusSeconds(30), 2, 1);
        assertThat(consulting.getStatus()).isEqualTo(ConsultingStatus.PENDING);
        assertThat(consulting.getNextRetryAt()).isEqualTo(NOW.plusSeconds(30));

        assertThat(consulting.claim(NOW.plusSeconds(30), Duration.ofMinutes(2), 2)).isTrue();
        consulting.fail("permanent", NOW.plusSeconds(30), NOW.plusSeconds(60), 2, 2);

        assertThat(consulting.getAttemptCount()).isEqualTo(2);
        assertThat(consulting.getStatus()).isEqualTo(ConsultingStatus.FAILED);
        assertThat(consulting.getNextRetryAt()).isNull();
        assertThat(consulting.claim(NOW.plusSeconds(90), Duration.ofMinutes(2), 2)).isFalse();
    }

    @Test
    void staleWorkerCannotOverwriteAReclaimedAttempt() {
        Consulting consulting = Consulting.pending(3L, LocalDate.of(2026, 7, 13), NOW);
        consulting.claim(NOW, Duration.ofMinutes(1), 3);
        consulting.claim(NOW.plusSeconds(60), Duration.ofMinutes(1), 3);

        boolean staleCompletion = consulting.complete(
                new ConsultingGenerationResult("오래된 결과", "사용하면 안 됩니다."),
                NOW.plusSeconds(61),
                1
        );
        boolean currentCompletion = consulting.complete(
                new ConsultingGenerationResult("최신 결과", "현재 임대의 결과입니다."),
                NOW.plusSeconds(62),
                2
        );

        assertThat(staleCompletion).isFalse();
        assertThat(currentCompletion).isTrue();
        assertThat(consulting.getTitle()).isEqualTo("최신 결과");
    }
}
