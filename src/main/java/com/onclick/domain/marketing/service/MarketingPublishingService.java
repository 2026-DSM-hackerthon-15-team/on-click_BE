package com.onclick.domain.marketing.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.entity.MarketingStatus;
import com.onclick.domain.marketing.repository.MarketingContentRepository;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class MarketingPublishingService {

    private final MarketingContentRepository repository;
    private final MarketingPublishWorker publishWorker;
    private final Clock clock;

    public MarketingPublishingService(
            MarketingContentRepository repository,
            MarketingPublishWorker publishWorker,
            Clock clock
    ) {
        this.repository = repository;
        this.publishWorker = publishWorker;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApproved(MarketingApprovedEvent event) {
        publishWorker.publish(event.marketingId());
    }

    @Scheduled(fixedDelayString = "${app.instagram.publish-recovery-interval:PT1M}")
    public void recoverPendingPublishes() {
        Instant now = clock.instant();
        List<MarketingContent> candidates = repository.findAllByStatusInAndNextPublishAtLessThanEqual(
                List.of(MarketingStatus.APPROVED),
                now
        );
        for (MarketingContent candidate : candidates) {
            publishWorker.publish(candidate.getId());
        }
        List<MarketingContent> uncertain = repository.findAllByStatusInAndNextPublishAtLessThanEqual(
                List.of(MarketingStatus.PUBLISHING),
                now
        );
        for (MarketingContent candidate : uncertain) {
            publishWorker.failStaleInFlight(candidate.getId());
        }
    }
}
