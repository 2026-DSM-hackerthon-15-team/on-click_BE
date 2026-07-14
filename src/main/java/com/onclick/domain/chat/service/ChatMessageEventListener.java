package com.onclick.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
class ChatMessageEventListener {

    private final ChatMessageDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageRequested(ChatMessageRequestedEvent event) {
        try {
            dispatcher.dispatch(event.assistantMessageId());
        } catch (RuntimeException exception) {
            // The message is already committed as PENDING and the recovery scheduler will retry it.
            log.warn(
                    "Could not dispatch chat message {}; recovery will retry it",
                    event.assistantMessageId(),
                    exception
            );
        }
    }
}
