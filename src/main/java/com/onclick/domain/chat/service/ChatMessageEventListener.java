package com.onclick.domain.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ChatMessageEventListener {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageEventListener.class);

    private final ChatMessageDispatcher dispatcher;

    ChatMessageEventListener(ChatMessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

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
