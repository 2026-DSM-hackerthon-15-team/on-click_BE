package com.onclick.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.chat.config.ChatProcessingProperties;
import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatMessageRole;
import com.onclick.domain.chat.entity.ChatMessageStatus;
import com.onclick.domain.chat.entity.ChatRoom;
import com.onclick.domain.chat.repository.ChatMessageRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMessageWorkServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatProcessingProperties properties;
    private ChatMessageWorkService workService;

    @BeforeEach
    void setUp() {
        properties = new ChatProcessingProperties();
        properties.setLeaseDuration(Duration.ofMinutes(2));
        properties.setRetryDelay(Duration.ofSeconds(10));
        properties.setMaxAttempts(3);
        workService = new ChatMessageWorkService(
                chatMessageRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void atomicallyClaimsPendingMessageAndBuildsOrderedHistory() {
        ChatRoom room = room(3L, 10L);
        ChatMessage oldUser = message(ChatMessage.user(room, "이전 질문"), 90L);
        ChatMessage oldAssistant = assistant(room, 91L, 90L, ChatMessageStatus.COMPLETED, "이전 답변", 1);
        ChatMessage user = message(ChatMessage.user(room, "현재 질문"), 100L);
        ChatMessage assistant = assistant(room, 101L, 100L, ChatMessageStatus.PENDING, "", 1);

        given(chatMessageRepository.claimPending(
                101L,
                NOW,
                NOW.plus(Duration.ofMinutes(2))
        )).willReturn(1);
        given(chatMessageRepository.findWithChatRoomById(101L)).willReturn(Optional.of(assistant));
        given(chatMessageRepository.findByIdAndChatRoom_Id(100L, 10L)).willReturn(Optional.of(user));
        given(chatMessageRepository.findRecentCompletedHistory(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(100L),
                any(Pageable.class)
        )).willReturn(List.of(oldAssistant, oldUser));

        ChatGenerationWork work = workService.claim(101L).orElseThrow();

        assertThat(work.attempt()).isEqualTo(1);
        assertThat(work.request().storeId()).isEqualTo(3L);
        assertThat(work.request().message()).isEqualTo("현재 질문");
        assertThat(work.request().history())
                .extracting(item -> item.role(), item -> item.content())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ChatMessageRole.USER, "이전 질문"),
                        org.assertj.core.groups.Tuple.tuple(ChatMessageRole.ASSISTANT, "이전 답변")
                );
    }

    @Test
    void leavesFailedGenerationPendingWithBackoffBeforeLastAttempt() {
        ChatGenerationWork work = new ChatGenerationWork(101L, 2, null);

        workService.fail(work);

        verify(chatMessageRepository).recordFailure(
                101L,
                2,
                ChatMessageStatus.PENDING,
                "",
                NOW.plusSeconds(20),
                NOW
        );
    }

    @Test
    void marksGenerationFailedAfterConfiguredAttempts() {
        ChatGenerationWork work = new ChatGenerationWork(101L, 3, null);

        workService.fail(work);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatMessageRepository).recordFailure(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(ChatMessageStatus.FAILED),
                contentCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
        assertThat(contentCaptor.getValue()).contains("답변을 생성하지 못했습니다");
    }

    private ChatRoom room(Long storeId, Long roomId) {
        ChatRoom room = ChatRoom.create(storeId, "채팅");
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private ChatMessage message(ChatMessage message, Long id) {
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private ChatMessage assistant(
            ChatRoom room,
            Long id,
            Long requestMessageId,
            ChatMessageStatus status,
            String content,
            int retryCount
    ) {
        ChatMessage message = ChatMessage.pendingAssistant(room, requestMessageId);
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "status", status);
        ReflectionTestUtils.setField(message, "content", content);
        ReflectionTestUtils.setField(message, "retryCount", retryCount);
        return message;
    }
}
