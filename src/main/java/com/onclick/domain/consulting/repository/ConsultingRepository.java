package com.onclick.domain.consulting.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.consulting.entity.Consulting;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsultingRepository extends JpaRepository<Consulting, Long> {

    List<Consulting> findAllByStoreIdOrderByTargetDateDescIdDesc(Long storeId);

    Optional<Consulting> findByIdAndStoreId(Long consultingId, Long storeId);

    Optional<Consulting> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT consulting FROM Consulting consulting WHERE consulting.id = :consultingId")
    Optional<Consulting> findByIdForUpdate(@Param("consultingId") Long consultingId);

    @Query("""
            SELECT consulting.id
             FROM Consulting consulting
             WHERE consulting.status = com.onclick.domain.consulting.entity.ConsultingStatus.PENDING
               AND (consulting.nextRetryAt IS NULL OR consulting.nextRetryAt <= :now)
             ORDER BY consulting.targetDate ASC, consulting.id ASC
            """)
    List<Long> findRetryableIds(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
