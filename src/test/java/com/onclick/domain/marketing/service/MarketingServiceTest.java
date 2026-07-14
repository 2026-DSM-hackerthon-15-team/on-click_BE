package com.onclick.domain.marketing.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationResult;
import com.onclick.domain.auth.entity.User;
import com.onclick.domain.marketing.dto.MarketingGenerateRequest;
import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.repository.MarketingContentRepository;
import com.onclick.domain.media.entity.MediaFile;
import com.onclick.domain.media.service.MediaStorageService;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MarketingServiceTest {

    @Mock MarketingContentRepository repository;
    @Mock MediaStorageService mediaStorageService;
    @Mock StoreAccessValidator storeAccessValidator;
    @Mock AiClient aiClient;
    @Mock Jwt jwt;

    MarketingService service;

    @BeforeEach
    void setUp() {
        service = new MarketingService(
                repository,
                mediaStorageService,
                storeAccessValidator,
                aiClient,
                Clock.fixed(Instant.parse("2026-07-14T07:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void mapsMarketingApiRequestToDocumentedAiCopyContract() {
        Store store = store(3L, 7L, "성수 카페");
        MediaFile media = media(3L, 11L);
        MarketingGenerateRequest request = new MarketingGenerateRequest(
                "딸기 라떼",
                "국내산 딸기와 수제 크림",
                6_500L,
                "오후 2시 이후 10% 할인",
                "직장인",
                "친근하고 자연스럽게",
                "여름 분위기를 강조해 주세요.",
                List.of(11L)
        );
        given(storeAccessValidator.validate(jwt, 3L)).willReturn(store);
        given(mediaStorageService.requireOwned(3L, List.of(11L))).willReturn(List.of(media));
        given(mediaStorageService.publicUrl(media)).willReturn("https://cdn.example.com/menu.jpg");
        given(aiClient.generateMarketing(any(MarketingGenerationRequest.class)))
                .willReturn(new MarketingGenerationResult("완성된 홍보 문구", "claude-sonnet-4-6"));
        given(repository.save(any(MarketingContent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = service.generate(jwt, 3L, request);

        ArgumentCaptor<MarketingGenerationRequest> captor =
                ArgumentCaptor.forClass(MarketingGenerationRequest.class);
        verify(aiClient).generateMarketing(captor.capture());
        MarketingGenerationRequest aiRequest = captor.getValue();
        assertThat(aiRequest.userId()).isEqualTo(7L);
        assertThat(aiRequest.imageUrls()).containsExactly("https://cdn.example.com/menu.jpg");
        assertThat(aiRequest.draftText()).isEqualTo("""
                상품명: 딸기 라떼
                설명: 국내산 딸기와 수제 크림
                가격: 6500
                프로모션: 오후 2시 이후 10% 할인
                대상 고객: 직장인""");
        assertThat(aiRequest.tags()).containsExactly("#딸기라떼", "#성수카페");
        assertThat(aiRequest.tone()).isEqualTo("친근하고 자연스럽게");
        assertThat(aiRequest.additionalRequest()).isEqualTo("여름 분위기를 강조해 주세요.");
        assertThat(response.content()).isEqualTo("완성된 홍보 문구");
        assertThat(response.hashtags()).containsExactly("#딸기라떼", "#성수카페");
    }

    @Test
    void rejectsCombinedDraftTextLongerThanAiContractBeforeCallingAi() {
        Store store = store(3L, 7L, "성수 카페");
        MediaFile media = media(3L, 11L);
        MarketingGenerateRequest request = new MarketingGenerateRequest(
                "가".repeat(100),
                "나".repeat(2000),
                null,
                null,
                null,
                null,
                null,
                List.of(11L)
        );
        given(storeAccessValidator.validate(jwt, 3L)).willReturn(store);
        given(mediaStorageService.requireOwned(3L, List.of(11L))).willReturn(List.of(media));

        assertThatThrownBy(() -> service.generate(jwt, 3L, request))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(aiClient);
    }

    private Store store(Long storeId, Long ownerId, String name) {
        User owner = new User("owner", "hash");
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Store store = new Store(owner, name);
        ReflectionTestUtils.setField(store, "id", storeId);
        return store;
    }

    private MediaFile media(Long storeId, Long mediaId) {
        MediaFile media = new MediaFile(storeId, "menu.jpg", "stored.jpg", "image/jpeg", 100L);
        ReflectionTestUtils.setField(media, "id", mediaId);
        return media;
    }
}
