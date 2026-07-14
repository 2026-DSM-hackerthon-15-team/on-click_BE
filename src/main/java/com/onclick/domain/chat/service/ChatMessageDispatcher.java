package com.onclick.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ChatMessageDispatcher {

    private final ChatMessageProcessor processor;

    @Async
    public void dispatch(Long assistantMessageId) {
        try {
            processor.process(assistantMessageId);
        } catch (RuntimeException exception) {
            log.error("Unexpected chat processing failure for message {}", assistantMessageId, exception);
        }
    }
}
