package com.onclick.domain.consulting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
class ConsultingGenerationEventListener {

    private final ConsultingJobDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationRequested(ConsultingGenerationRequestedEvent event) {
        try {
            dispatcher.dispatch(event.consultingId());
        } catch (RuntimeException exception) {
            // The job is already committed as PENDING and the scheduler will recover it.
            log.warn(
                    "Could not dispatch consulting {}; scheduler recovery will retry it",
                    event.consultingId(),
                    exception
            );
        }
    }
}
