package com.onclick.domain.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ChatPendingMessageRecovery {

    private static final Logger log = LoggerFactory.getLogger(ChatPendingMessageRecovery.class);

    private final ChatMessageWorkService workService;
    private final ChatMessageDispatcher dispatcher;

    ChatPendingMessageRecovery(
            ChatMessageWorkService workService,
            ChatMessageDispatcher dispatcher
    ) {
        this.workService = workService;
        this.dispatcher = dispatcher;
    }

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
