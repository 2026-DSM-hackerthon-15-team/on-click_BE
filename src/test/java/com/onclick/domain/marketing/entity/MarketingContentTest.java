package com.onclick.domain.marketing.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.onclick.domain.media.entity.MediaFile;

import org.junit.jupiter.api.Test;

class MarketingContentTest {

    @Test
    void followsDraftApprovalPublishingAndPublishedLifecycle() {
        MediaFile media = new MediaFile(3L, "menu.jpg", "stored.jpg", "image/jpeg", 100L);
        MarketingContent marketing = new MarketingContent(
                3L,
                "신메뉴",
                "신메뉴를 만나보세요.",
                List.of("신메뉴", "온클릭"),
                List.of(media)
        );
        Instant now = Instant.parse("2026-07-14T12:00:00Z");

        marketing.approve(now);
        marketing.beginPublishing(now);
        marketing.markPublished("post-1", "https://instagram.example/post-1", now.plusSeconds(2));

        assertThat(marketing.getStatus()).isEqualTo(MarketingStatus.PUBLISHED);
        assertThat(marketing.getPublishAttemptCount()).isEqualTo(1);
        assertThat(marketing.hashtagList()).containsExactly("#신메뉴", "#온클릭");
        assertThat(marketing.getExternalPostId()).isEqualTo("post-1");
    }

    @Test
    void cannotApproveWithoutMediaOrEditAfterApproval() {
        MarketingContent noMedia = new MarketingContent(3L, "제목", "본문", List.of(), List.of());
        assertThatThrownBy(() -> noMedia.approve(Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("media");

        MarketingContent marketing = new MarketingContent(
                3L,
                "제목",
                "본문",
                List.of(),
                List.of(new MediaFile(3L, "menu.jpg", "stored.jpg", "image/jpeg", 100L))
        );
        marketing.approve(Instant.now());
        assertThatThrownBy(() -> marketing.edit("수정", null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
