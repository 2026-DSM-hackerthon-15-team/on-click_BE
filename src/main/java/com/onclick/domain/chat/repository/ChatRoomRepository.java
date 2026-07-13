package com.onclick.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import com.onclick.domain.chat.entity.ChatRoom;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    List<ChatRoom> findAllByStoreIdOrderByUpdatedAtDescIdDesc(Long storeId);

    Optional<ChatRoom> findByIdAndStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ChatRoom room where room.id = :id and room.storeId = :storeId")
    Optional<ChatRoom> findByIdAndStoreIdForUpdate(@Param("id") Long id, @Param("storeId") Long storeId);
}
