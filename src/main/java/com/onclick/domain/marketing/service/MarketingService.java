package com.onclick.domain.marketing.service;

import java.util.List;

import com.onclick.common.ai.AiClient;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationResult;
import com.onclick.domain.marketing.dto.MarketingGenerateRequest;
import com.onclick.domain.marketing.dto.MarketingResponse;
import com.onclick.domain.marketing.dto.MarketingUpdateRequest;
import com.onclick.domain.marketing.entity.MarketingContent;
import com.onclick.domain.marketing.repository.MarketingContentRepository;
import com.onclick.domain.media.entity.MediaFile;
import com.onclick.domain.media.dto.MediaUploadResponse;
import com.onclick.domain.media.service.MediaStorageService;
import com.onclick.domain.product.entity.Product;
import com.onclick.domain.product.repository.ProductRepository;
import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.service.StoreAccessValidator;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MarketingService {

    private final MarketingContentRepository repository;
    private final MediaStorageService mediaStorageService;
    private final ProductRepository productRepository;
    private final StoreAccessValidator storeAccessValidator;
    private final AiClient aiClient;

    @Transactional
    public MarketingResponse generate(Jwt jwt, Long storeId, MarketingGenerateRequest request) {
        Store store = storeAccessValidator.validate(jwt, storeId);
        List<MediaFile> mediaFiles = mediaStorageService.requireOwned(storeId, request.mediaIds());
        List<String> hashtags = defaultHashtags(store, request.productName());
        String draftText = buildDraftText(request);
        List<String> imageUrls = mediaFiles.stream()
                .map(mediaStorageService::publicUrl)
                .toList();
        MarketingGenerationResult generated = aiClient.generateMarketing(new MarketingGenerationRequest(
                store.getOwnerUserId(),
                imageUrls,
                draftText,
                hashtags,
                request.tone(),
                request.additionalRequest()
        ));
        MarketingContent marketing = repository.save(new MarketingContent(
                storeId,
                request.productName().trim() + " Instagram 홍보",
                generated.content(),
                hashtags,
                mediaFiles
        ));
        return MarketingResponse.from(marketing, mediaStorageService);
    }

    @Transactional
    public MarketingResponse generate(Jwt jwt, Long storeId, Long productId, MultipartFile image) {
        Store store = storeAccessValidator.validate(jwt, storeId);
        if (productId == null || productId <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "productId를 입력해 주세요.");
        }
        Product product = productRepository.findByIdAndStoreId(productId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isActive()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "비활성 상품은 홍보할 수 없습니다.");
        }

        MediaUploadResponse uploadResponse = mediaStorageService.upload(jwt, storeId, image);
        MarketingGenerateRequest request = new MarketingGenerateRequest(
                product.getName(),
                defaultProductDescription(product),
                product.getPrice(),
                null,
                null,
                null,
                null,
                List.of(uploadResponse.mediaId())
        );
        return generate(jwt, storeId, request);
    }

    private String defaultProductDescription(Product product) {
        return "%s의 판매가 %s인 상품입니다.".formatted(
                product.getName(),
                String.format("%,d원", product.getPrice())
        );
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

    private MarketingContent find(Long storeId, Long marketingId) {
        return repository.findByIdAndStoreId(marketingId, storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.MARKETING_NOT_FOUND));
    }

    private String buildDraftText(MarketingGenerateRequest request) {
        StringBuilder draftText = new StringBuilder()
                .append("상품명: ").append(request.productName().trim())
                .append("\n설명: ").append(request.description().trim());
        append(draftText, "가격", request.price());
        append(draftText, "프로모션", request.promotion());
        append(draftText, "대상 고객", request.targetAudience());
        if (draftText.length() > 2000) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "AI 마케팅 초안 입력은 2000자를 초과할 수 없습니다."
            );
        }
        return draftText.toString();
    }

    private void append(StringBuilder builder, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            builder.append('\n').append(label).append(": ").append(value.toString().trim());
        }
    }

    private List<String> defaultHashtags(Store store, String productName) {
        return List.of("#" + productName.replaceAll("\\s+", ""), "#" + store.getName().replaceAll("\\s+", ""));
    }
}
