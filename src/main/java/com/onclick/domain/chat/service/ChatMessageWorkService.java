package com.onclick.domain.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.chat.config.ChatProcessingProperties;
import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatMessageRole;
import com.onclick.domain.chat.entity.ChatMessageStatus;
import com.onclick.domain.chat.generation.ChatGenerationRequest;
import com.onclick.domain.chat.generation.ChatHistoryItem;
import com.onclick.domain.chat.repository.ChatMessageRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChatMessageWorkService {

    static final int HISTORY_LIMIT = 20;
    static final String FAILED_MESSAGE = "답변을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private final ChatMessageRepository chatMessageRepository;
    private final ChatProcessingProperties properties;
    private final Clock clock;

    ChatMessageWorkService(
            ChatMessageRepository chatMessageRepository,
            ChatProcessingProperties properties,
            Clock clock
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<ChatGenerationWork> claim(Long assistantMessageId) {
        Instant now = Instant.now(clock);
        int claimed = chatMessageRepository.claimPending(
                assistantMessageId,
                now,
                now.plus(properties.safeLeaseDuration())
        );
        if (claimed == 0) {
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

        List<ChatMessage> recentHistory = new ArrayList<>(
                chatMessageRepository.findRecentCompletedHistory(
                        chatRoomId,
                        userMessage.getId(),
                        PageRequest.of(0, HISTORY_LIMIT)
                )
        );
        Collections.reverse(recentHistory);
        List<ChatHistoryItem> history = recentHistory.stream()
                .map(message -> new ChatHistoryItem(message.getRole(), message.getContent()))
                .toList();

        return Optional.of(new ChatGenerationWork(
                assistantMessage.getId(),
                assistantMessage.getRetryCount(),
                new ChatGenerationRequest(
                        assistantMessage.getChatRoom().getStoreId(),
                        chatRoomId,
                        userMessage.getId(),
                        userMessage.getContent(),
                        history
                )
        ));
    }

    @Transactional
    public void complete(ChatGenerationWork work, String content) {
        chatMessageRepository.completeClaim(
                work.assistantMessageId(),
                work.attempt(),
                content,
                Instant.now(clock)
        );
    }

    @Transactional
    public void fail(ChatGenerationWork work) {
        Instant now = Instant.now(clock);
        boolean exhausted = work.attempt() >= properties.safeMaxAttempts();
        ChatMessageStatus status = exhausted
                ? ChatMessageStatus.FAILED
                : ChatMessageStatus.PENDING;
        String content = exhausted ? FAILED_MESSAGE : "";
        Instant nextRetryAt = exhausted
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
                Instant.now(clock),
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
}
