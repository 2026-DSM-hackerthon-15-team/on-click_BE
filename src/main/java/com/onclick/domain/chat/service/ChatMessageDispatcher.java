package com.onclick.domain.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
class ChatMessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageDispatcher.class);

    private final ChatMessageProcessor processor;

    ChatMessageDispatcher(ChatMessageProcessor processor) {
        this.processor = processor;
    }

    @Async
    public void dispatch(Long assistantMessageId) {
        try {
            processor.process(assistantMessageId);
        } catch (RuntimeException exception) {
            log.error("Unexpected chat processing failure for message {}", assistantMessageId, exception);
        }
    }
}
