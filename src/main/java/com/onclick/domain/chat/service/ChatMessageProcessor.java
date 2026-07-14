package com.onclick.domain.chat.service;

import java.util.Optional;

import com.onclick.domain.chat.generation.ChatGenerationPort;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ChatMessageProcessor {

    private final ChatMessageWorkService workService;
    private final ChatGenerationPort generationPort;
    private final ChatResponseComposer responseComposer;

    void process(Long assistantMessageId) {
        Optional<ChatGenerationWork> claimedWork = workService.claim(assistantMessageId);
        if (claimedWork.isEmpty()) {
            return;
        }
        ChatGenerationWork work = claimedWork.orElseThrow();
        log.info(
                "Chat generation started: assistantMessageId={}, attempt={}",
                work.assistantMessageId(),
                work.attempt()
        );

        String response;
        try {
            response = responseComposer.normalizeGeneratedResponse(
                    generationPort.generate(work.request())
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Chat response generation failed for message {} on attempt {}",
                    work.assistantMessageId(),
                    work.attempt(),
                    exception
            );
            workService.fail(work, isRetryable(exception));
            return;
        }

        workService.complete(work, response);
        log.info(
                "Chat generation completed: assistantMessageId={}, attempt={}",
                work.assistantMessageId(),
                work.attempt()
        );
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.errorCode() == ErrorCode.AI_SERVICE_UNAVAILABLE;
        }
        return true;
    }
}
