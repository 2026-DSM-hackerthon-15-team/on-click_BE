package com.onclick.domain.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.onclick.common.time.KoreanTime;
import com.onclick.domain.chat.config.ChatProcessingProperties;
import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatMessageRole;
import com.onclick.domain.chat.entity.ChatMessageStatus;
import com.onclick.domain.chat.generation.ChatGenerationRequest;
import com.onclick.domain.chat.repository.ChatMessageRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ChatMessageWorkService {

    static final String FAILED_MESSAGE = "답변을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private final ChatMessageRepository chatMessageRepository;
    private final StoreRepository storeRepository;
    private final ChatProcessingProperties properties;
    private final Clock clock;

    @Transactional
    public Optional<ChatGenerationWork> claim(Long assistantMessageId) {
        LocalDateTime now = now();
        int maxAttempts = properties.safeMaxAttempts();
        int claimed = chatMessageRepository.claimPending(
                assistantMessageId,
                now,
                now.plus(properties.safeLeaseDuration()),
                maxAttempts
        );
        if (claimed == 0) {
            chatMessageRepository.failExpiredExhausted(
                    assistantMessageId,
                    now,
                    maxAttempts,
                    FAILED_MESSAGE
            );
            return Optional.empty();
        }

        ChatMessage assistantMessage = chatMessageRepository.findWithChatRoomById(assistantMessageId)
                .orElseThrow(() -> new IllegalStateException("Claimed chat message no longer exists"));
        Long chatRoomId = assistantMessage.getChatRoomId();
        ChatMessage userMessage = chatMessageRepository
                .findByIdAndChatRoom_Id(assistantMessage.getRequestMessageId(), chatRoomId)
                .filter(message -> message.getRole() == ChatMessageRole.USER)
                .filter(message -> message.getStatus() == ChatMessageStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Chat request message is invalid"));

        Store store = storeRepository.findById(assistantMessage.getChatRoom().getStoreId())
                .orElseThrow(() -> new IllegalStateException("Chat room store no longer exists"));

        return Optional.of(new ChatGenerationWork(
                assistantMessage.getId(),
                assistantMessage.getRetryCount(),
                new ChatGenerationRequest(
                        store.getOwnerUserId(),
                        store.getId(),
                        chatRoomId,
                        userMessage.getContent()
                )
        ));
    }

    @Transactional
    public void complete(ChatGenerationWork work, String content) {
        chatMessageRepository.completeClaim(
                work.assistantMessageId(),
                work.attempt(),
                content,
                now()
        );
    }

    @Transactional
    public void fail(ChatGenerationWork work) {
        fail(work, true);
    }

    @Transactional
    public void fail(ChatGenerationWork work, boolean retryable) {
        LocalDateTime now = now();
        boolean exhausted = !retryable || work.attempt() >= properties.safeMaxAttempts();
        ChatMessageStatus status = exhausted
                ? ChatMessageStatus.FAILED
                : ChatMessageStatus.PENDING;
        String content = exhausted ? FAILED_MESSAGE : "";
        LocalDateTime nextRetryAt = exhausted
                ? null
                : now.plus(retryDelayForAttempt(work.attempt()));
        chatMessageRepository.recordFailure(
                work.assistantMessageId(),
                work.attempt(),
                status,
                content,
                nextRetryAt,
                now
        );
    }

    @Transactional(readOnly = true)
    public List<Long> findEligibleMessageIds() {
        return chatMessageRepository.findEligibleMessageIds(
                ChatMessageRole.ASSISTANT,
                ChatMessageStatus.PENDING,
                now(),
                PageRequest.of(0, properties.safeRecoveryBatchSize())
        );
    }

    private Duration retryDelayForAttempt(int attempt) {
        try {
            return properties.safeRetryDelay().multipliedBy(Math.max(1, attempt));
        } catch (ArithmeticException exception) {
            return properties.safeRetryDelay();
        }
    }

    private LocalDateTime now() {
        return KoreanTime.now(clock);
    }
}
