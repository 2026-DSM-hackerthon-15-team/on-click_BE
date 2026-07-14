package com.onclick.domain.chat.service;

import java.time.Clock;
import java.util.List;

import com.onclick.common.time.KoreanTime;
import com.onclick.domain.chat.dto.ChatMessageCreateRequest;
import com.onclick.domain.chat.dto.ChatMessageExchangeResponse;
import com.onclick.domain.chat.dto.ChatMessageResponse;
import com.onclick.domain.chat.dto.ChatRoomCreateRequest;
import com.onclick.domain.chat.dto.ChatRoomDetailResponse;
import com.onclick.domain.chat.dto.ChatRoomResponse;
import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatRoom;
import com.onclick.domain.chat.repository.ChatMessageRepository;
import com.onclick.domain.chat.repository.ChatRoomRepository;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    static final String DEFAULT_ROOM_TITLE = "새 채팅";
    static final int MAX_TITLE_LENGTH = 100;
    static final int MAX_MESSAGE_LENGTH = 4_000;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public ChatRoomResponse createRoom(
            Jwt jwt,
            Long storeId,
            ChatRoomCreateRequest request
    ) {
        storeAccessValidator.validate(jwt, storeId);
        String title = normalizeTitle(request == null ? null : request.title());
        return ChatRoomResponse.from(chatRoomRepository.save(ChatRoom.create(storeId, title)));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> findRooms(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        return chatRoomRepository.findAllByStoreIdOrderByUpdatedAtDescIdDesc(storeId).stream()
                .map(ChatRoomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailResponse findRoom(Jwt jwt, Long storeId, Long chatRoomId) {
        storeAccessValidator.validate(jwt, storeId);
        ChatRoom room = findOwnedRoom(storeId, chatRoomId);
        List<ChatMessageResponse> messages = chatMessageRepository
                .findAllByChatRoom_IdOrderByIdAsc(room.getId())
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        return ChatRoomDetailResponse.from(room, messages);
    }

    @Transactional
    public void deleteRoom(Jwt jwt, Long storeId, Long chatRoomId) {
        storeAccessValidator.validate(jwt, storeId);
        chatRoomRepository.delete(findOwnedRoom(storeId, chatRoomId));
    }

    @Transactional
    public ChatMessageExchangeResponse sendMessage(
            Jwt jwt,
            Long storeId,
            Long chatRoomId,
            ChatMessageCreateRequest request
    ) {
        storeAccessValidator.validate(jwt, storeId);
        ChatRoom room = findOwnedRoomForUpdate(storeId, chatRoomId);
        String content = normalizeMessage(request == null ? null : request.content());
        String clientMessageId = normalizeClientMessageId(request == null ? null : request.clientMessageId());

        if (clientMessageId != null) {
            ChatMessage existing = chatMessageRepository
                    .findByChatRoom_IdAndClientMessageId(room.getId(), clientMessageId)
                    .orElse(null);
            if (existing != null) {
                if (!existing.getContent().equals(content)) {
                    throw new ApiException(ErrorCode.CHAT_MESSAGE_CONFLICT);
                }
                ChatMessage assistant = chatMessageRepository
                        .findByChatRoom_IdAndRequestMessageId(room.getId(), existing.getId())
                        .orElseThrow(() -> new IllegalStateException("Assistant response placeholder is missing"));
                return new ChatMessageExchangeResponse(
                        ChatMessageResponse.from(existing),
                        ChatMessageResponse.from(assistant)
                );
            }
        }

        ChatMessage userMessage = chatMessageRepository.saveAndFlush(
                ChatMessage.user(room, content, clientMessageId)
        );
        ChatMessage assistantMessage = chatMessageRepository.saveAndFlush(
                ChatMessage.pendingAssistant(room, userMessage.getId())
        );
        room.touch(KoreanTime.now(clock));

        eventPublisher.publishEvent(new ChatMessageRequestedEvent(assistantMessage.getId()));
        return new ChatMessageExchangeResponse(
                ChatMessageResponse.from(userMessage),
                ChatMessageResponse.from(assistantMessage)
        );
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> findMessages(
            Jwt jwt,
            Long storeId,
            Long chatRoomId,
            Long afterId
    ) {
        storeAccessValidator.validate(jwt, storeId);
        ChatRoom room = findOwnedRoom(storeId, chatRoomId);
        if (afterId != null && afterId < 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "afterId는 0 이상이어야 합니다.");
        }

        List<ChatMessage> messages = afterId == null
                ? chatMessageRepository.findAllByChatRoom_IdOrderByIdAsc(room.getId())
                : chatMessageRepository.findPollingMessages(
                        room.getId(),
                        afterId
                );
        return messages.stream().map(ChatMessageResponse::from).toList();
    }

    private ChatRoom findOwnedRoom(Long storeId, Long chatRoomId) {
        if (chatRoomId == null || chatRoomId <= 0) {
            throw new ApiException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        return chatRoomRepository.findByIdAndStoreId(chatRoomId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private ChatRoom findOwnedRoomForUpdate(Long storeId, Long chatRoomId) {
        if (chatRoomId == null || chatRoomId <= 0) {
            throw new ApiException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        return chatRoomRepository.findByIdAndStoreIdForUpdate(chatRoomId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            return DEFAULT_ROOM_TITLE;
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "채팅방 제목은 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private String normalizeMessage(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "메시지 내용을 입력해 주세요.");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "메시지는 4000자 이하여야 합니다.");
        }
        return normalized;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return null;
        }
        String normalized = clientMessageId.trim();
        if (normalized.length() > 100) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "clientMessageId는 100자 이하여야 합니다.");
        }
        return normalized;
    }
}
