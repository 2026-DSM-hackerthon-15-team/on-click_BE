package com.onclick.domain.marketing.service;

import java.time.Clock;
import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationResult;
import com.onclick.common.time.KoreanTime;
import com.onclick.domain.marketing.dto.MarketingGenerateRequest;
import com.onclick.domain.marketing.dto.MarketingResponse;
import com.onclick.domain.marketing.dto.MarketingUpdateRequest;
import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.repository.MarketingContentRepository;
import com.onclick.domain.media.entity.MediaFile;
import com.onclick.domain.media.service.MediaStorageService;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketingService {

    private final MarketingContentRepository repository;
    private final MediaStorageService mediaStorageService;
    private final StoreAccessValidator storeAccessValidator;
    private final AiClient aiClient;
    private final Clock clock;

    @Transactional
    public MarketingResponse generate(Jwt jwt, Long storeId, MarketingGenerateRequest request) {
        Store store = storeAccessValidator.validate(jwt, storeId);
        List<MediaFile> mediaFiles = mediaStorageService.requireOwned(storeId, request.mediaIds());
        MarketingGenerationResult generated = aiClient.generateMarketing(new MarketingGenerationRequest(
                storeId,
                store.getName(),
                buildPrompt(request)
        ));
        MarketingContent marketing = repository.save(new MarketingContent(
                storeId,
                request.productName().trim() + " Instagram 홍보",
                generated.content(),
                defaultHashtags(store, request.productName()),
                mediaFiles
        ));
        return MarketingResponse.from(marketing, mediaStorageService);
    }

    @Transactional(readOnly = true)
    public List<MarketingResponse> findAll(Jwt jwt, Long storeId) {
        storeAccessValidator.validate(jwt, storeId);
        return repository.findAllByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(marketing -> MarketingResponse.from(marketing, mediaStorageService))
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketingResponse findOne(Jwt jwt, Long storeId, Long marketingId) {
        storeAccessValidator.validate(jwt, storeId);
        return MarketingResponse.from(find(storeId, marketingId), mediaStorageService);
    }

    @Transactional
    public MarketingResponse update(
            Jwt jwt,
            Long storeId,
            Long marketingId,
            MarketingUpdateRequest request
    ) {
        storeAccessValidator.validate(jwt, storeId);
        if (request.title() == null
                && request.content() == null
                && request.hashtags() == null
                && request.mediaIds() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "수정할 마케팅 콘텐츠를 입력해 주세요.");
        }
        MarketingContent marketing = find(storeId, marketingId);
        List<MediaFile> mediaFiles = request.mediaIds() == null
                ? null
                : mediaStorageService.requireOwned(storeId, request.mediaIds());
        try {
            marketing.edit(request.title(), request.content(), request.hashtags(), mediaFiles);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.MARKETING_STATUS_CONFLICT);
        }
        return MarketingResponse.from(marketing, mediaStorageService);
    }

    @Transactional
    public MarketingResponse approve(Jwt jwt, Long storeId, Long marketingId) {
        storeAccessValidator.validate(jwt, storeId);
        MarketingContent marketing = find(storeId, marketingId);
        try {
            marketing.approve(KoreanTime.now(clock));
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.MARKETING_STATUS_CONFLICT, exception.getMessage());
        }
        return MarketingResponse.from(marketing, mediaStorageService);
    }

    private MarketingContent find(Long storeId, Long marketingId) {
        return repository.findByIdAndStoreId(marketingId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.MARKETING_NOT_FOUND));
    }

    private String buildPrompt(MarketingGenerateRequest request) {
        StringBuilder prompt = new StringBuilder()
                .append("상품명: ").append(request.productName().trim())
                .append("\n설명: ").append(request.description().trim());
        append(prompt, "가격", request.price());
        append(prompt, "프로모션", request.promotion());
        append(prompt, "대상 고객", request.targetAudience());
        append(prompt, "톤", request.tone());
        append(prompt, "추가 요청", request.additionalRequest());
        prompt.append("\nInstagram 게시용 한국어 본문과 적절한 해시태그를 생성해 주세요.");
        return prompt.toString();
    }

    private void append(StringBuilder builder, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            builder.append('\n').append(label).append(": ").append(value);
        }
    }

    private List<String> defaultHashtags(Store store, String productName) {
        return List.of("#" + productName.replaceAll("\\s+", ""), "#" + store.getName().replaceAll("\\s+", ""));
    }
}
