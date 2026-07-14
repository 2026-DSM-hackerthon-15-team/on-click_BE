package com.onclick.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.onclick.domain.chat.config.ChatProcessingProperties;
import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatMessageStatus;
import com.onclick.domain.chat.entity.ChatRoom;
import com.onclick.domain.chat.repository.ChatMessageRepository;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.repository.StoreRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMessageWorkServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-07-14T12:00:00Z");
    private static final LocalDateTime NOW_KST = LocalDateTime.of(2026, 7, 14, 21, 0);

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private StoreRepository storeRepository;

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
                storeRepository,
                properties,
                Clock.fixed(NOW_INSTANT, ZoneOffset.UTC)
        );
    }

    @Test
    void atomicallyClaimsPendingMessageAndBuildsAiRequestContext() {
        ChatRoom room = room(3L, 10L);
        ChatMessage user = message(ChatMessage.user(room, "현재 질문"), 100L);
        ChatMessage assistant = assistant(room, 101L, 100L, ChatMessageStatus.PENDING, "", 1);
        Store store = store(7L, 3L);

        given(chatMessageRepository.claimPending(
                101L,
                NOW_KST,
                NOW_KST.plus(Duration.ofMinutes(2)),
                3
        )).willReturn(1);
        given(chatMessageRepository.findWithChatRoomById(101L)).willReturn(Optional.of(assistant));
        given(chatMessageRepository.findByIdAndChatRoom_Id(100L, 10L)).willReturn(Optional.of(user));
        given(storeRepository.findById(3L)).willReturn(Optional.of(store));

        ChatGenerationWork work = workService.claim(101L).orElseThrow();

        assertThat(work.attempt()).isEqualTo(1);
        assertThat(work.request().userId()).isEqualTo(7L);
        assertThat(work.request().storeId()).isEqualTo(3L);
        assertThat(work.request().chatRoomId()).isEqualTo(10L);
        assertThat(work.request().message()).isEqualTo("현재 질문");
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
                NOW_KST.plusSeconds(20),
                NOW_KST
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
                org.mockito.ArgumentMatchers.eq(NOW_KST)
        );
        assertThat(contentCaptor.getValue()).contains("답변을 생성하지 못했습니다");
    }

    @Test
    void marksNonRetryableFailureImmediately() {
        ChatGenerationWork work = new ChatGenerationWork(101L, 1, null);

        workService.fail(work, false);

        verify(chatMessageRepository).recordFailure(
                101L,
                1,
                ChatMessageStatus.FAILED,
                ChatMessageWorkService.FAILED_MESSAGE,
                null,
                NOW_KST
        );
    }

    @Test
    void expiresLastCrashedAttemptWithoutClaimingAgain() {
        given(chatMessageRepository.claimPending(
                101L,
                NOW_KST,
                NOW_KST.plus(Duration.ofMinutes(2)),
                3
        )).willReturn(0);

        Optional<ChatGenerationWork> work = workService.claim(101L);

        assertThat(work).isEmpty();
        verify(chatMessageRepository).failExpiredExhausted(
                101L,
                NOW_KST,
                3,
                ChatMessageWorkService.FAILED_MESSAGE
        );
    }

    private ChatRoom room(Long storeId, Long roomId) {
        ChatRoom room = ChatRoom.create(storeId, "채팅");
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private Store store(Long userId, Long storeId) {
        User owner = new User("owner", "hash");
        ReflectionTestUtils.setField(owner, "id", userId);
        Store store = new Store(owner, "매장");
        ReflectionTestUtils.setField(store, "id", storeId);
        return store;
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
