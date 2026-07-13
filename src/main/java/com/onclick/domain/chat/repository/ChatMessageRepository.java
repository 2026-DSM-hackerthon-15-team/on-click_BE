package com.onclick.domain.chat.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.chat.entity.ChatMessage;
import com.onclick.domain.chat.entity.ChatMessageRole;
import com.onclick.domain.chat.entity.ChatMessageStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findAllByChatRoom_IdOrderByIdAsc(Long chatRoomId);

    List<ChatMessage> findAllByChatRoom_IdAndIdGreaterThanOrderByIdAsc(
            Long chatRoomId,
            Long afterId
    );

    Optional<ChatMessage> findByIdAndChatRoom_Id(Long id, Long chatRoomId);

    Optional<ChatMessage> findByChatRoom_IdAndClientMessageId(Long chatRoomId, String clientMessageId);

    Optional<ChatMessage> findByChatRoom_IdAndRequestMessageId(Long chatRoomId, Long requestMessageId);

    @EntityGraph(attributePaths = "chatRoom")
    @Query("SELECT message FROM ChatMessage message WHERE message.id = :id")
    Optional<ChatMessage> findWithChatRoomById(@Param("id") Long id);

    @Query("""
            SELECT message
              FROM ChatMessage message
             WHERE message.chatRoom.id = :chatRoomId
               AND message.id < :beforeId
               AND message.status = com.onclick.domain.chat.entity.ChatMessageStatus.COMPLETED
             ORDER BY message.id DESC
            """)
    List<ChatMessage> findRecentCompletedHistory(
            @Param("chatRoomId") Long chatRoomId,
            @Param("beforeId") Long beforeId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatMessage message
               SET message.retryCount = message.retryCount + 1,
                   message.nextRetryAt = :leaseUntil,
                   message.updatedAt = :now
             WHERE message.id = :messageId
               AND message.role = com.onclick.domain.chat.entity.ChatMessageRole.ASSISTANT
               AND message.status = com.onclick.domain.chat.entity.ChatMessageStatus.PENDING
               AND (message.nextRetryAt IS NULL OR message.nextRetryAt <= :now)
            """)
    int claimPending(
            @Param("messageId") Long messageId,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatMessage message
               SET message.status = com.onclick.domain.chat.entity.ChatMessageStatus.COMPLETED,
                   message.content = :content,
                   message.nextRetryAt = NULL,
                   message.updatedAt = :now
             WHERE message.id = :messageId
               AND message.role = com.onclick.domain.chat.entity.ChatMessageRole.ASSISTANT
               AND message.status = com.onclick.domain.chat.entity.ChatMessageStatus.PENDING
               AND message.retryCount = :attempt
            """)
    int completeClaim(
            @Param("messageId") Long messageId,
            @Param("attempt") int attempt,
            @Param("content") String content,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChatMessage message
               SET message.status = :status,
                   message.content = :content,
                   message.nextRetryAt = :nextRetryAt,
                   message.updatedAt = :now
             WHERE message.id = :messageId
               AND message.role = com.onclick.domain.chat.entity.ChatMessageRole.ASSISTANT
               AND message.status = com.onclick.domain.chat.entity.ChatMessageStatus.PENDING
               AND message.retryCount = :attempt
            """)
    int recordFailure(
            @Param("messageId") Long messageId,
            @Param("attempt") int attempt,
            @Param("status") ChatMessageStatus status,
            @Param("content") String content,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("now") Instant now
    );

    @Query("""
            SELECT message.id
              FROM ChatMessage message
             WHERE message.role = :role
               AND message.status = :status
               AND (message.nextRetryAt IS NULL OR message.nextRetryAt <= :now)
             ORDER BY message.id ASC
            """)
    List<Long> findEligibleMessageIds(
            @Param("role") ChatMessageRole role,
            @Param("status") ChatMessageStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
