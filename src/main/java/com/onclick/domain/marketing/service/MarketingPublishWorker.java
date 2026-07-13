package com.onclick.domain.marketing.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.onclick.domain.instagram.service.InstagramIntegrationService;
import com.onclick.domain.instagram.service.InstagramIntegrationService.PublishingCredentials;
import com.onclick.domain.instagram.service.InstagramProvider;
import com.onclick.domain.instagram.service.InstagramProviderException;
import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.entity.MarketingStatus;
import com.onclick.domain.marketing.repository.MarketingContentRepository;
import com.onclick.domain.media.service.MediaStorageService;
import com.onclick.global.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MarketingPublishWorker {

    private static final int MAX_ATTEMPTS = 3;
    private static final String UNCERTAIN_MESSAGE =
            "Instagram 게시 요청의 결과를 확정할 수 없습니다. Instagram에서 게시 여부를 확인한 뒤 다시 승인해 주세요.";

    private final MarketingContentRepository repository;
    private final InstagramIntegrationService integrationService;
    private final InstagramProvider instagramProvider;
    private final MediaStorageService mediaStorageService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public MarketingPublishWorker(
            MarketingContentRepository repository,
            InstagramIntegrationService integrationService,
            InstagramProvider instagramProvider,
            MediaStorageService mediaStorageService,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.integrationService = integrationService;
        this.instagramProvider = instagramProvider;
        this.mediaStorageService = mediaStorageService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void publish(Long marketingId) {
        PublishCommand command = transactionTemplate.execute(status -> claim(marketingId));
        if (command == null) {
            return;
        }

        try {
            PublishingCredentials credentials = integrationService.requirePublishingCredentials(command.storeId());
            InstagramProvider.PublishedPost published = instagramProvider.publish(new InstagramProvider.PublishRequest(
                    credentials.instagramUserId(),
                    credentials.accessToken(),
                    command.caption(),
                    command.imageUrls(),
                    command.idempotencyKey()
            ));
            transactionTemplate.executeWithoutResult(status -> complete(command, published));
        } catch (InstagramProviderException exception) {
            transactionTemplate.executeWithoutResult(status -> fail(
                    command,
                    exception.isDeliveryUncertain() ? UNCERTAIN_MESSAGE : exception.getMessage(),
                    exception.isRetryable() && !exception.isDeliveryUncertain()
            ));
        } catch (ApiException exception) {
            transactionTemplate.executeWithoutResult(status -> fail(command, exception.getMessage(), false));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> fail(command, UNCERTAIN_MESSAGE, false));
        }
    }

    public void failStaleInFlight(Long marketingId) {
        transactionTemplate.executeWithoutResult(status -> {
            MarketingContent marketing = repository.findForPublishingById(marketingId).orElse(null);
            if (marketing != null
                    && marketing.getStatus() == MarketingStatus.PUBLISHING
                    && marketing.getNextPublishAt() != null
                    && !marketing.getNextPublishAt().isAfter(clock.instant())) {
                marketing.markPublishUncertain(UNCERTAIN_MESSAGE);
            }
        });
    }

    private PublishCommand claim(Long marketingId) {
        MarketingContent marketing = repository.findForPublishingById(marketingId).orElse(null);
        if (marketing == null || marketing.getStatus() != MarketingStatus.APPROVED) {
            return null;
        }
        Instant now = clock.instant();
        if (marketing.getNextPublishAt() != null && marketing.getNextPublishAt().isAfter(now)) {
            return null;
        }
        marketing.beginPublishing(now);
        String caption = marketing.getContent();
        if (!marketing.hashtagList().isEmpty()) {
            caption += "\n\n" + String.join(" ", marketing.hashtagList());
        }
        return new PublishCommand(
                marketing.getId(),
                marketing.getStoreId(),
                marketing.getPublishAttemptCount(),
                caption,
                marketing.getMediaFiles().stream().map(mediaStorageService::publicUrl).toList(),
                marketing.getIdempotencyKey()
        );
    }

    private void complete(PublishCommand command, InstagramProvider.PublishedPost published) {
        MarketingContent marketing = repository.findForPublishingById(command.marketingId()).orElse(null);
        if (!ownsClaim(marketing, command)) {
            return;
        }
        marketing.markPublished(published.externalPostId(), published.permalink(), published.publishedAt());
    }

    private void fail(PublishCommand command, String reason, boolean retryable) {
        MarketingContent marketing = repository.findForPublishingById(command.marketingId()).orElse(null);
        if (!ownsClaim(marketing, command)) {
            return;
        }
        boolean shouldRetry = retryable && command.attempt() < MAX_ATTEMPTS;
        long delaySeconds = 60L << Math.max(0, command.attempt() - 1);
        marketing.markPublishFailed(reason, clock.instant().plus(Duration.ofSeconds(delaySeconds)), shouldRetry);
    }

    private boolean ownsClaim(MarketingContent marketing, PublishCommand command) {
        return marketing != null
                && marketing.getStatus() == MarketingStatus.PUBLISHING
                && marketing.getPublishAttemptCount() == command.attempt();
    }

    private record PublishCommand(
            Long marketingId,
            Long storeId,
            int attempt,
            String caption,
            List<String> imageUrls,
            String idempotencyKey
    ) {
    }
}
