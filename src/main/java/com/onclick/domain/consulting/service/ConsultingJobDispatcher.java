package com.onclick.domain.consulting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ConsultingJobDispatcher {

    private final ConsultingJobProcessor processor;

    @Async
    public void dispatch(Long consultingId) {
        try {
            processor.process(consultingId);
        } catch (RuntimeException exception) {
            log.error("Unexpected consulting processing failure for consulting {}", consultingId, exception);
        }
    }
}
