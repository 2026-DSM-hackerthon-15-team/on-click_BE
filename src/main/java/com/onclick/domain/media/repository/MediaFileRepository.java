package com.onclick.domain.media.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.media.entity.MediaFile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    Optional<MediaFile> findByIdAndStoreId(Long id, Long storeId);

    Optional<MediaFile> findByPublicId(String publicId);

    List<MediaFile> findAllByIdInAndStoreId(Collection<Long> ids, Long storeId);

    @Query("select media.storageName from MediaFile media")
    List<String> findAllStorageNames();

    @Query("""
            select media
            from MediaFile media
            where media.createdAt < :cutoff
              and not exists (
                  select marketing.id
                  from MarketingContent marketing
                  join marketing.mediaFiles attached
                  where attached.id = media.id
              )
            """)
    List<MediaFile> findOrphansCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select count(marketing)
            from MarketingContent marketing
            join marketing.mediaFiles attached
            where attached.id = :mediaId
            """)
    long countMarketingReferences(@Param("mediaId") Long mediaId);
}
