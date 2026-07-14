package com.onclick.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.chat.dto.ChatMessageCreateRequest;
import com.onclick.domain.chat.dto.ChatRoomCreateRequest;
import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatMessageRole;
import com.onclick.domain.chat.entity.ChatMessageStatus;
import com.onclick.domain.chat.entity.ChatRoom;
import com.onclick.domain.chat.repository.ChatMessageRepository;
import com.onclick.domain.chat.repository.ChatRoomRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-07-14T12:00:00Z");
    private static final LocalDateTime NOW_KST = LocalDateTime.of(2026, 7, 14, 21, 0);

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private StoreAccessValidator storeAccessValidator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Jwt jwt;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                chatRoomRepository,
                chatMessageRepository,
                storeAccessValidator,
                eventPublisher,
                Clock.fixed(NOW_INSTANT, ZoneOffset.UTC)
        );
    }

    @Test
    void createsRoomWithDefaultTitle() {
        given(chatRoomRepository.save(org.mockito.ArgumentMatchers.any(ChatRoom.class)))
                .willAnswer(invocation -> roomWithId(invocation.getArgument(0), 10L));

        var response = chatService.createRoom(jwt, 3L, new ChatRoomCreateRequest("   "));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.storeId()).isEqualTo(3L);
        assertThat(response.title()).isEqualTo("새 채팅");
        verify(storeAccessValidator).validate(jwt, 3L);
    }

    @Test
    void persistsUserAndPendingAssistantBeforePublishingWork() {
        ChatRoom room = roomWithId(ChatRoom.create(3L, "운영 상담"), 10L);
        given(chatRoomRepository.findByIdAndStoreIdForUpdate(10L, 3L)).willReturn(Optional.of(room));
        given(chatMessageRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ChatMessage.class)))
                .willAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    long id = message.getRole() == ChatMessageRole.USER ? 100L : 101L;
                    ReflectionTestUtils.setField(message, "id", id);
                    ReflectionTestUtils.setField(message, "createdAt", NOW_KST);
                    ReflectionTestUtils.setField(message, "updatedAt", NOW_KST);
                    return message;
                });

        var response = chatService.sendMessage(
                jwt,
                3L,
                10L,
                new ChatMessageCreateRequest("  오늘 매출을 분석해 줘  ")
        );

        assertThat(response.userMessage().id()).isEqualTo(100L);
        assertThat(response.userMessage().role()).isEqualTo(ChatMessageRole.USER);
        assertThat(response.userMessage().status()).isEqualTo(ChatMessageStatus.COMPLETED);
        assertThat(response.userMessage().content()).isEqualTo("오늘 매출을 분석해 줘");
        assertThat(response.userMessage().clientMessageId()).isNull();
        assertThat(response.assistantMessage().id()).isEqualTo(101L);
        assertThat(response.assistantMessage().role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(response.assistantMessage().status()).isEqualTo(ChatMessageStatus.PENDING);
        assertThat(response.assistantMessage().content()).isNull();

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2))
                .saveAndFlush(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(1).getRequestMessageId()).isEqualTo(100L);
        verify(eventPublisher).publishEvent(new ChatMessageRequestedEvent(101L));
    }

    @Test
    void returnsExistingExchangeForRepeatedClientMessageId() {
        ChatRoom room = roomWithId(ChatRoom.create(3L, "운영 상담"), 10L);
        ChatMessage user = ChatMessage.user(room, "오늘 매출을 분석해 줘", "pos-message-1");
        ReflectionTestUtils.setField(user, "id", 100L);
        ReflectionTestUtils.setField(user, "createdAt", NOW_KST);
        ReflectionTestUtils.setField(user, "updatedAt", NOW_KST);
        ChatMessage assistant = ChatMessage.pendingAssistant(room, 100L);
        ReflectionTestUtils.setField(assistant, "id", 101L);
        ReflectionTestUtils.setField(assistant, "createdAt", NOW_KST);
        ReflectionTestUtils.setField(assistant, "updatedAt", NOW_KST);
        given(chatRoomRepository.findByIdAndStoreIdForUpdate(10L, 3L)).willReturn(Optional.of(room));
        given(chatMessageRepository.findByChatRoom_IdAndClientMessageId(10L, "pos-message-1"))
                .willReturn(Optional.of(user));
        given(chatMessageRepository.findByChatRoom_IdAndRequestMessageId(10L, 100L))
                .willReturn(Optional.of(assistant));

        var response = chatService.sendMessage(
                jwt,
                3L,
                10L,
                new ChatMessageCreateRequest("pos-message-1", "오늘 매출을 분석해 줘")
        );

        assertThat(response.userMessage().id()).isEqualTo(100L);
        assertThat(response.userMessage().clientMessageId()).isEqualTo("pos-message-1");
        assertThat(response.assistantMessage().id()).isEqualTo(101L);
        verify(chatMessageRepository, org.mockito.Mockito.never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRoomFromAnotherStore() {
        given(chatRoomRepository.findByIdAndStoreId(10L, 3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.findRoom(jwt, 3L, 10L))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
        verify(storeAccessValidator).validate(jwt, 3L);
    }

    @Test
    void pollsOnlyMessagesAfterRequestedId() {
        ChatRoom room = roomWithId(ChatRoom.create(3L, "운영 상담"), 10L);
        ChatMessage message = ChatMessage.user(room, "새 질문");
        ReflectionTestUtils.setField(message, "id", 12L);
        ReflectionTestUtils.setField(message, "createdAt", NOW_KST);
        ReflectionTestUtils.setField(message, "updatedAt", NOW_KST);
        given(chatRoomRepository.findByIdAndStoreId(10L, 3L)).willReturn(Optional.of(room));
        given(chatMessageRepository.findPollingMessages(10L, 11L))
                .willReturn(List.of(message));

        var response = chatService.findMessages(jwt, 3L, 10L, 11L);

        assertThat(response).singleElement().extracting(item -> item.id()).isEqualTo(12L);
        verify(chatMessageRepository)
                .findPollingMessages(10L, 11L);
    }

    @Test
    void rejectsNegativePollingCursor() {
        ChatRoom room = roomWithId(ChatRoom.create(3L, "운영 상담"), 10L);
        given(chatRoomRepository.findByIdAndStoreId(10L, 3L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> chatService.findMessages(jwt, 3L, 10L, -1L))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private ChatRoom roomWithId(ChatRoom room, Long id) {
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "createdAt", NOW_KST);
        ReflectionTestUtils.setField(room, "updatedAt", NOW_KST);
        return room;
    }
}
