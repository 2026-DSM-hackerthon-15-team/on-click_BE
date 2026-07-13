package com.onclick.domain.chat.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
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
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatMessage() {
    }

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

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public Long getChatRoomId() {
        return chatRoom.getId();
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public ChatMessageStatus getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public Long getRequestMessageId() {
        return requestMessageId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
