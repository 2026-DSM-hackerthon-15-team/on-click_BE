package com.onclick.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ChatPendingMessageRecovery {

    private final ChatMessageWorkService workService;
    private final ChatMessageDispatcher dispatcher;

    @Scheduled(
            initialDelayString = "${app.chat.processing.recovery-initial-delay-ms:5000}",
            fixedDelayString = "${app.chat.processing.recovery-delay-ms:5000}"
    )
    public void recoverPendingMessages() {
        workService.findEligibleMessageIds().forEach(messageId -> {
            try {
                dispatcher.dispatch(messageId);
            } catch (RuntimeException exception) {
                log.warn("Could not redispatch pending chat message {}", messageId, exception);
            }
        });
    }
}
