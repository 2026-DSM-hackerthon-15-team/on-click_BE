package com.onclick.domain.chat.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "chat_rooms",
        indexes = @Index(
                name = "idx_chat_rooms_store_updated",
                columnList = "store_id,updated_at"
        )
)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages = new ArrayList<>();

    protected ChatRoom() {
    }

    private ChatRoom(Long storeId, String title) {
        this.storeId = Objects.requireNonNull(storeId, "storeId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
    }

    public static ChatRoom create(Long storeId, String title) {
        return new ChatRoom(storeId, title);
    }

    public void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }
}
