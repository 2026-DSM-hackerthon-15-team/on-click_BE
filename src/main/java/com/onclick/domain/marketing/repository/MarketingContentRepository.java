package com.onclick.domain.marketing.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.entity.MarketingStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketingContentRepository extends JpaRepository<MarketingContent, Long> {

    @EntityGraph(attributePaths = "mediaFiles")
    List<MarketingContent> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);

    @EntityGraph(attributePaths = "mediaFiles")
    Optional<MarketingContent> findByIdAndStoreId(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select marketing from MarketingContent marketing where marketing.id = :id")
    Optional<MarketingContent> findForPublishingById(@Param("id") Long id);

    @EntityGraph(attributePaths = "mediaFiles")
    List<MarketingContent> findAllByStatusInAndNextPublishAtLessThanEqual(
            Collection<MarketingStatus> statuses,
            Instant nextPublishAt
    );
}
