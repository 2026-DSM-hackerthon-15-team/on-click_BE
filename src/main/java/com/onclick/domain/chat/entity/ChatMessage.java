package com.onclick.domain.chat.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "chat_messages",
        indexes = {
                @Index(
                        name = "idx_chat_messages_room_id",
                        columnList = "chat_room_id,id"
                ),
                @Index(
                        name = "idx_chat_messages_retry",
                        columnList = "status,next_retry_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatMessageStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "request_message_id")
    private Long requestMessageId;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt;

    private ChatMessage(
            ChatRoom chatRoom,
            ChatMessageRole role,
            ChatMessageStatus status,
            String content,
            Long requestMessageId,
            String clientMessageId
    ) {
        this.chatRoom = Objects.requireNonNull(chatRoom, "chatRoom must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.requestMessageId = requestMessageId;
        this.clientMessageId = clientMessageId;
    }

    public static ChatMessage user(ChatRoom chatRoom, String content) {
        return user(chatRoom, content, null);
    }

    public static ChatMessage user(ChatRoom chatRoom, String content, String clientMessageId) {
        return new ChatMessage(
                chatRoom,
                ChatMessageRole.USER,
                ChatMessageStatus.COMPLETED,
                content,
                null,
                clientMessageId
        );
    }

    public static ChatMessage pendingAssistant(ChatRoom chatRoom, Long requestMessageId) {
        return new ChatMessage(
                chatRoom,
                ChatMessageRole.ASSISTANT,
                ChatMessageStatus.PENDING,
                "",
                Objects.requireNonNull(requestMessageId, "requestMessageId must not be null"),
                null
        );
    }

    public Long getChatRoomId() {
        return chatRoom.getId();
    }
}
