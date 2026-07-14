package com.onclick.domain.marketing.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import com.onclick.common.ai.dto.InstagramImageAttachment;
import com.onclick.common.ai.dto.InstagramPublishRequest;
import com.onclick.common.ai.dto.InstagramPublishResult;
import com.onclick.common.ai.dto.InstagramPublishStatus;
import com.onclick.common.time.KoreanTime;
import com.onclick.domain.instagram.service.InstagramAccountService;
import com.onclick.domain.instagram.service.InstagramAccountService.BrowserCredentials;
import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.entity.MarketingStatus;
import com.onclick.domain.marketing.repository.MarketingContentRepository;
import com.onclick.domain.media.service.MediaStorageService;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MarketingPublicationTransactions {

    private final MarketingContentRepository repository;
    private final StoreAccessValidator storeAccessValidator;
    private final InstagramAccountService instagramAccountService;
    private final MediaStorageService mediaStorageService;
    private final Clock clock;

    @Transactional
    public PreparedPublication prepare(Jwt jwt, Long storeId, Long marketingId) {
        Store store = storeAccessValidator.validate(jwt, storeId);
        MarketingContent marketing = repository.findForPublishingById(marketingId)
                .orElseThrow(() -> new ApiException(ErrorCode.MARKETING_NOT_FOUND));
        if (!storeId.equals(marketing.getStoreId())) {
            throw new ApiException(ErrorCode.MARKETING_NOT_FOUND);
        }
        BrowserCredentials credentials = instagramAccountService.requireCredentials(storeId);

        try {
            marketing.approve(KoreanTime.now(clock));
            marketing.beginPublishing(KoreanTime.now(clock));
            List<InstagramImageAttachment> images = marketing.getMediaFiles().stream()
                    .map(mediaFile -> {
                        Path path = mediaStorageService.resolveStoredPath(mediaFile);
                        try {
                            return new InstagramImageAttachment(
                                    mediaFile.getOriginalName(),
                                    Files.readAllBytes(path),
                                    mediaFile.getContentType()
                            );
                        } catch (IOException exception) {
                            throw new ApiException(ErrorCode.MEDIA_STORAGE_ERROR, "이미지 파일을 읽을 수 없습니다.", exception);
                        }
                    })
                    .toList();
            InstagramPublishRequest request = new InstagramPublishRequest(
                    store.getOwnerUserId(),
                    credentials.accountId(),
                    credentials.password(),
                    marketing.getContent(),
                    marketing.hashtagList(),
                    images,
                    marketing.getIdempotencyKey()
            );
            return new PreparedPublication(marketing.getId(), request);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_INSTAGRAM_POST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.MARKETING_STATUS_CONFLICT, exception.getMessage());
        }
    }

    @Transactional
    public void complete(Long marketingId, InstagramPublishResult result) {
        MarketingContent marketing = findForPublishing(marketingId);
        if (result.status() == InstagramPublishStatus.PUBLISHED) {
            marketing.markPublished(
                    result.externalPostId(),
                    result.publishedUrl(),
                    result.publishedAt()
            );
            return;
        }
        if (result.status() == InstagramPublishStatus.FAILED) {
            marketing.markPublishFailed(result.failureReason(), null, false);
            return;
        }
        throw new ApiException(ErrorCode.AI_RESPONSE_INVALID);
    }

    @Transactional
    public void fail(Long marketingId, String reason) {
        MarketingContent marketing = repository.findForPublishingById(marketingId).orElse(null);
        if (marketing != null && marketing.getStatus() == MarketingStatus.PUBLISHING) {
            marketing.markPublishUncertain(reason);
        }
    }

    private MarketingContent findForPublishing(Long marketingId) {
        MarketingContent marketing = repository.findForPublishingById(marketingId)
                .orElseThrow(() -> new ApiException(ErrorCode.MARKETING_NOT_FOUND));
        if (marketing.getStatus() != MarketingStatus.PUBLISHING) {
            throw new ApiException(ErrorCode.MARKETING_STATUS_CONFLICT);
        }
        return marketing;
    }

    public record PreparedPublication(Long marketingId, InstagramPublishRequest request) {
    }
}
