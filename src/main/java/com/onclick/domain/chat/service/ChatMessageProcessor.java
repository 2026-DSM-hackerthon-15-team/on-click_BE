package com.onclick.domain.chat.service;

import java.util.Optional;

import com.onclick.domain.chat.generation.ChatGenerationPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class ChatMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageProcessor.class);

    private final ChatMessageWorkService workService;
    private final ChatGenerationPort generationPort;
    private final ChatResponseComposer responseComposer;

    ChatMessageProcessor(
            ChatMessageWorkService workService,
            ChatGenerationPort generationPort,
            ChatResponseComposer responseComposer
    ) {
        this.workService = workService;
        this.generationPort = generationPort;
        this.responseComposer = responseComposer;
    }

    void process(Long assistantMessageId) {
        Optional<ChatGenerationWork> claimedWork = workService.claim(assistantMessageId);
        if (claimedWork.isEmpty()) {
            return;
        }
        ChatGenerationWork work = claimedWork.orElseThrow();

        String response;
        try {
            response = responseComposer.actionResponse(
                            work.request().storeId(),
                            work.request().message()
                    )
                    .orElseGet(() -> responseComposer.normalizeGeneratedResponse(
                            generationPort.generate(work.request())
                    ));
        } catch (RuntimeException exception) {
            log.warn(
                    "Chat response generation failed for message {} on attempt {}",
                    work.assistantMessageId(),
                    work.attempt(),
                    exception
            );
            workService.fail(work);
            return;
        }

        workService.complete(work, response);
    }
}
