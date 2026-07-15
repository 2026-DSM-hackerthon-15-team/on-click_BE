package com.onclick.common.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.onclick.common.ai.dto.ChatGenerationRequest;
import com.onclick.common.ai.dto.ChatGenerationResult;
import com.onclick.common.ai.dto.ClosingSalesForecastRequest;
import com.onclick.common.ai.dto.ClosingSalesForecastResult;
import com.onclick.common.ai.dto.ConsultingGenerationRequest;
import com.onclick.common.ai.dto.ConsultingGenerationResult;
import com.onclick.common.ai.dto.MarketingGenerationRequest;
import com.onclick.common.ai.dto.MarketingGenerationResult;
import com.onclick.common.ai.dto.InstagramImageAttachment;
import com.onclick.common.ai.dto.InstagramPublishRequest;
import com.onclick.common.ai.dto.InstagramPublishResult;
import com.onclick.common.ai.dto.InstagramPublishStatus;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "http")
@Slf4j
public class HttpAiClient implements AiClient {

    private static final Set<String> CHAT_FINISH_REASONS = Set.of(
            "STOP",
            "TOOL_ERROR",
            "MAX_TOKENS",
            "SAFETY"
    );
    private static final Set<String> TOOL_STATUSES = Set.of("SUCCESS", "FAILED", "SKIPPED");
    private static final Set<String> RECOMMENDATION_PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");

    private final RestClient restClient;
    private final AiHttpProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpAiClient(AiHttpProperties properties) {
        this(properties, createRestClient(properties), createObjectMapper());
    }

    HttpAiClient(AiHttpProperties properties, RestClient restClient) {
        this(properties, restClient, createObjectMapper());
    }

    HttpAiClient(AiHttpProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public ClosingSalesForecastResult forecastClosingSales(ClosingSalesForecastRequest request) {
        String path = properties.getPaths().getClosingSales();
        return mapResponse(path, () -> {
            ClosingSalesForecastWireResponse response = post(
                    path,
                    request,
                    ClosingSalesForecastWireResponse.class
            );
            validateClosingSalesResponse(request, response);
            return new ClosingSalesForecastResult(
                    response.forecastClosingSalesAmount(),
                    response.generatedAt()
            );
        });
    }

    @Override
    public TomorrowVisitorsForecastResult forecastTomorrowVisitors(TomorrowVisitorsForecastRequest request) {
        String path = properties.getPaths().getTomorrowVisitors();
        return mapResponse(path, () -> {
            TomorrowVisitorsForecastWireResponse response = post(
                    path,
                    request,
                    TomorrowVisitorsForecastWireResponse.class
            );
            validateTomorrowVisitorsResponse(request, response);
            return new TomorrowVisitorsForecastResult(
                    response.expectedVisitors(),
                    response.generatedAt()
            );
        });
    }

    @Override
    public ConsultingGenerationResult generateDailyConsulting(ConsultingGenerationRequest request) {
        String path = properties.getPaths().getDailyConsulting();
        return mapResponse(path, () -> {
            ConsultingGenerationWireResponse response = post(
                    path,
                    request,
                    ConsultingGenerationWireResponse.class
            );
            validateDailyConsultingResponse(request, response);
            return new ConsultingGenerationResult(response.title(), response.content());
        });
    }

    @Override
    public ChatGenerationResult generateChatReply(ChatGenerationRequest request) {
        String path = properties.getPaths().getChat();
        return mapResponse(path, () -> {
            ChatGenerationWireResponse response = post(
                    path,
                    request,
                    ChatGenerationWireResponse.class
            );
            validateChatResponse(response);
            return new ChatGenerationResult(response.answer());
        });
    }

    @Override
    public MarketingGenerationResult generateMarketing(MarketingGenerationRequest request) {
        String path = properties.getPaths().getMarketing();
        try {
            validateMarketingRequest(request);
        } catch (RuntimeException exception) {
            throw failure(path, exception, ErrorCode.AI_REQUEST_REJECTED);
        }
        return mapResponse(path, () -> {
            MarketingGenerationWireResponse response = post(
                    path,
                    request,
                    MarketingGenerationWireResponse.class
            );
            return new MarketingGenerationResult(
                    requireText(response.content(), "content"),
                    requireText(response.model(), "model")
            );
        });
    }

    @Override
    public InstagramPublishResult publishInstagram(
            Long marketingId,
            InstagramPublishRequest request,
            String bearerToken
    ) {
        String path = properties.getPaths().getInstagramPublish();
        try {
            validateInstagramPublishRequest(marketingId, request, bearerToken);
        } catch (RuntimeException exception) {
            throw failure(path, exception, ErrorCode.INVALID_INSTAGRAM_POST);
        }

        try {
            log.info(
                    "AI Instagram publish started: path={}, marketingId={}, request={}",
                    path,
                    marketingId,
                    request
            );
            MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
            multipartBodyBuilder.part("userId", request.userId());
            multipartBodyBuilder.part("instagramUsername", request.instagramUsername());
            multipartBodyBuilder.part("instagramPassword", request.instagramPassword());
            multipartBodyBuilder.part("content", request.content());
            multipartBodyBuilder.part("hashtags", request.hashtags());
            multipartBodyBuilder.part("idempotencyKey", request.idempotencyKey());
            for (InstagramImageAttachment image : request.images()) {
                multipartBodyBuilder.part(
                        "images",
                        image.content(),
                        MediaType.APPLICATION_OCTET_STREAM
                ).filename(image.filename());
            }

            InstagramPublishResult response = restClient.post()
                    .uri(path, Map.of("marketingId", marketingId))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> applyAuthorizationHeader(headers, bearerToken))
                    .body(multipartBodyBuilder.build())
                    .retrieve()
                    .body(InstagramPublishResult.class);
            log.info(
                    "AI Instagram response received: path={}, marketingId={}, response={}",
                    path,
                    marketingId,
                    response
            );
            validateInstagramPublishResponse(marketingId, response);
            return response;
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw failure(path, exception, mapInstagramPublishError(exception));
        } catch (ResourceAccessException exception) {
            ErrorCode errorCode = hasCause(exception, HttpTimeoutException.class)
                    || hasCause(exception, SocketTimeoutException.class)
                    ? ErrorCode.INSTAGRAM_PUBLISH_TIMEOUT
                    : ErrorCode.AI_SERVICE_UNAVAILABLE;
            throw failure(path, exception, errorCode);
        } catch (RestClientException exception) {
            throw failure(path, exception, ErrorCode.AI_RESPONSE_INVALID);
        } catch (RuntimeException exception) {
            throw failure(path, exception, ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private void validateInstagramPublishRequest(
            Long marketingId,
            InstagramPublishRequest request,
            String bearerToken
    ) {
        if (marketingId == null || marketingId <= 0) {
            throw new IllegalArgumentException("marketingId must be positive");
        }
        Objects.requireNonNull(request, "request must not be null");
        if (request.userId() == null || request.userId() <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        requireTextWithin(request.instagramUsername(), "instagramUsername", 100);
        requireTextBetween(request.instagramPassword(), "instagramPassword", 8, 200);
        requireTextWithin(request.content(), "content", 2_200);
        if (request.hashtags().size() > 30) {
            throw new IllegalArgumentException("hashtags must contain at most 30 items");
        }
        for (String hashtag : request.hashtags()) {
            requireText(hashtag, "hashtags");
        }
        if (request.images().isEmpty() || request.images().size() > 10) {
            throw new IllegalArgumentException("images must contain between 1 and 10 items");
        }
        for (InstagramImageAttachment image : request.images()) {
            if (image == null || image.content() == null || image.content().length == 0) {
                throw new IllegalArgumentException("images must contain non-empty image attachments");
            }
        }
        requireTextWithin(request.idempotencyKey(), "idempotencyKey", 100);
        requireText(bearerToken, "bearerToken");
    }

    private void validateInstagramPublishResponse(Long marketingId, InstagramPublishResult response) {
        Objects.requireNonNull(response, "response must not be null");
        if (!Objects.equals(marketingId, response.marketingId())) {
            throw new IllegalArgumentException("marketingId does not match the request");
        }
        if (!"INSTAGRAM".equals(response.platform())) {
            throw new IllegalArgumentException("platform must be INSTAGRAM");
        }
        Objects.requireNonNull(response.status(), "status must not be null");
        if (response.status() == InstagramPublishStatus.PROCESSING) {
            throw new IllegalArgumentException("PROCESSING is not a final synchronous response");
        }
        if (response.status() == InstagramPublishStatus.PUBLISHED) {
            requireText(response.externalPostId(), "externalPostId");
            if (response.publishedUrl() == null || !response.publishedUrl().startsWith("https://")) {
                throw new IllegalArgumentException("publishedUrl must be an HTTPS URL");
            }
            Objects.requireNonNull(response.publishedAt(), "publishedAt must not be null");
        } else if (response.status() == InstagramPublishStatus.FAILED) {
            requireText(response.failureReason(), "failureReason");
        }
    }

    private ErrorCode mapInstagramPublishError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String responseBody = exception.getResponseBodyAsString().toUpperCase(java.util.Locale.ROOT);
        return switch (status) {
            case 400 -> ErrorCode.INVALID_INSTAGRAM_POST;
            case 409 -> ErrorCode.DUPLICATE_PUBLISH_REQUEST;
            case 422 -> responseBody.contains("CHALLENGE")
                    ? ErrorCode.INSTAGRAM_LOGIN_CHALLENGE_REQUIRED
                    : ErrorCode.INSTAGRAM_CREDENTIALS_INVALID;
            case 502 -> ErrorCode.BROWSER_MCP_UNAVAILABLE;
            case 504 -> ErrorCode.INSTAGRAM_PUBLISH_TIMEOUT;
            default -> status >= 500
                    ? ErrorCode.AI_SERVICE_UNAVAILABLE
                    : ErrorCode.AI_REQUEST_REJECTED;
        };
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void validateClosingSalesResponse(
            ClosingSalesForecastRequest request,
            ClosingSalesForecastWireResponse response
    ) {
        if (!Objects.equals(request.storeId(), response.storeId())) {
            throw new IllegalArgumentException("storeId does not match the request");
        }
        if (!Objects.equals(request.asOf().toLocalDate(), response.businessDate())) {
            throw new IllegalArgumentException("businessDate does not match the request");
        }
        if (!"KRW".equals(response.currency())) {
            throw new IllegalArgumentException("currency must be KRW");
        }
        requireNonNegative(response.observedSalesAmount(), "observedSalesAmount");
        requireNonNegative(response.forecastClosingSalesAmount(), "forecastClosingSalesAmount");
        requireText(response.model(), "model");
        requireNonNegative(response.sampleDays(), "sampleDays");
        Objects.requireNonNull(response.generatedAt(), "generatedAt must not be null");
    }

    private void validateTomorrowVisitorsResponse(
            TomorrowVisitorsForecastRequest request,
            TomorrowVisitorsForecastWireResponse response
    ) {
        if (!Objects.equals(request.storeId(), response.storeId())) {
            throw new IllegalArgumentException("storeId does not match the request");
        }
        if (!Objects.equals(request.baseDate().plusDays(1), response.targetDate())) {
            throw new IllegalArgumentException("targetDate must be the day after baseDate");
        }
        requireNonNegative(response.expectedVisitors(), "expectedVisitors");
        requireText(response.model(), "model");
        requireNonNegative(response.sampleDays(), "sampleDays");
        Objects.requireNonNull(response.generatedAt(), "generatedAt must not be null");
    }

    private void validateMarketingRequest(MarketingGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        List<String> imageUrls = requireList(request.imageUrls(), "imageUrls");
        if (imageUrls.isEmpty() || imageUrls.size() > 10) {
            throw new IllegalArgumentException("imageUrls must contain between 1 and 10 items");
        }
        for (String imageUrl : imageUrls) {
            if (imageUrl == null || !imageUrl.startsWith("https://")) {
                throw new IllegalArgumentException("imageUrls must contain HTTPS URLs");
            }
        }
        requireTextWithin(request.draftText(), "draftText", 2_000);
        if (request.tags() != null && request.tags().size() > 30) {
            throw new IllegalArgumentException("tags must contain at most 30 items");
        }
        requireOptionalTextWithin(request.tone(), "tone", 100);
        requireOptionalTextWithin(request.additionalRequest(), "additionalRequest", 500);
    }

    private void requireTextWithin(String value, String field, int maxLength) {
        String text = requireText(value, field);
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
    }

    private void requireTextBetween(String value, String field, int minLength, int maxLength) {
        String text = requireText(value, field);
        if (text.length() < minLength || text.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be between " + minLength + " and " + maxLength + " characters"
            );
        }
    }

    private void requireOptionalTextWithin(String value, String field, int maxLength) {
        if (value != null && !value.isBlank() && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
    }

    private long requireNonNegative(Long value, String field) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private int requireNonNegative(Integer value, String field) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private void validateChatResponse(ChatGenerationWireResponse response) {
        requireText(response.model(), "model");
        requireEnum(response.finishReason(), CHAT_FINISH_REASONS, "finishReason");
        validateToolExecutions(requireList(response.usedTools(), "usedTools"));
        if (response.citations() != null) {
            validateCitations(response.citations());
        }
    }

    private void validateDailyConsultingResponse(
            ConsultingGenerationRequest request,
            ConsultingGenerationWireResponse response
    ) {
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }
        if (!request.targetDate().equals(response.targetDate())) {
            throw new IllegalArgumentException("targetDate does not match the request");
        }
        String title = response.title();
        String content = response.content();
        if ((title == null || title.isBlank()) && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("response must include title or content");
        }
        if (response.summary() != null && !response.summary().isBlank()) {
            requireText(response.summary(), "summary");
        }
        if (response.model() != null && !response.model().isBlank()) {
            requireText(response.model(), "model");
        }
        if (response.chatInsights() != null) {
            requireList(response.chatInsights(), "chatInsights");
        }
        if (response.externalFactors() != null) {
            requireList(response.externalFactors(), "externalFactors");
        }
        if (response.warnings() != null) {
            requireList(response.warnings(), "warnings");
        }
        if (response.keyMetrics() != null) {
            validateMetrics(requireList(response.keyMetrics(), "keyMetrics"));
        }
        if (response.estimatedCauses() != null) {
            validateCauses(requireList(response.estimatedCauses(), "estimatedCauses"));
        }
        if (response.recommendations() != null) {
            validateRecommendations(requireList(response.recommendations(), "recommendations"));
        }
        if (response.usedTools() != null) {
            validateToolExecutions(requireList(response.usedTools(), "usedTools"));
        }
        if (response.citations() != null) {
            validateCitations(requireList(response.citations(), "citations"));
        }
    }

    private void validateToolExecutions(List<AiToolExecutionWireResponse> executions) {
        for (AiToolExecutionWireResponse execution : executions) {
            if (execution == null) {
                throw new IllegalArgumentException("usedTools must not contain null");
            }
            requireText(execution.toolName(), "usedTools.toolName");
            requireEnum(execution.status(), TOOL_STATUSES, "usedTools.status");
            if (execution.latencyMs() != null && execution.latencyMs() < 0) {
                throw new IllegalArgumentException("usedTools.latencyMs must be non-negative");
            }
        }
    }

    private void validateCitations(List<AiCitationWireResponse> citations) {
        for (AiCitationWireResponse citation : citations) {
            if (citation == null) {
                throw new IllegalArgumentException("citations must not contain null");
            }
            requireText(citation.title(), "citations.title");
            requireText(citation.url(), "citations.url");
        }
    }

    private void validateMetrics(List<ConsultingMetricWireResponse> metrics) {
        for (ConsultingMetricWireResponse metric : metrics) {
            if (metric == null || metric.currentValue() == null) {
                throw new IllegalArgumentException("keyMetrics values must not be null");
            }
            requireText(metric.metricName(), "keyMetrics.metricName");
            requireText(metric.unit(), "keyMetrics.unit");
        }
    }

    private void validateCauses(List<ConsultingCauseWireResponse> causes) {
        for (ConsultingCauseWireResponse cause : causes) {
            if (cause == null || cause.confidence() == null
                    || cause.confidence() < 0 || cause.confidence() > 1) {
                throw new IllegalArgumentException("estimatedCauses.confidence must be between 0 and 1");
            }
            requireText(cause.title(), "estimatedCauses.title");
            requireText(cause.description(), "estimatedCauses.description");
            requireList(cause.evidence(), "estimatedCauses.evidence");
        }
    }

    private void validateRecommendations(List<ConsultingRecommendationWireResponse> recommendations) {
        for (ConsultingRecommendationWireResponse recommendation : recommendations) {
            if (recommendation == null) {
                throw new IllegalArgumentException("recommendations must not contain null");
            }
            requireEnum(
                    recommendation.priority(),
                    RECOMMENDATION_PRIORITIES,
                    "recommendations.priority"
            );
            requireText(recommendation.title(), "recommendations.title");
            requireText(recommendation.description(), "recommendations.description");
        }
    }

    private <T> List<T> requireList(List<T> value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private void requireEnum(String value, Set<String> allowed, String field) {
        if (value == null || !allowed.contains(value)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private <T> T mapResponse(String path, Supplier<T> mapper) {
        try {
            return mapper.get();
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(path, exception, ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        Objects.requireNonNull(request, "request must not be null");
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info(
                        "AI request started: path={}, attempt={}/{}, request={}",
                        path,
                        attempt,
                        maxAttempts,
                        request
                );
                T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyAuthorizationHeader(headers, null))
                    .body(serializeRequest(request))
                    .retrieve()
                    .body(responseType);
                log.info(
                        "AI response received: path={}, attempt={}/{}, response={}",
                        path,
                        attempt,
                        maxAttempts,
                        response
                );
                if (response == null) {
                    throw failure(
                            path,
                            new IllegalStateException("AI response body must not be null"),
                            ErrorCode.AI_RESPONSE_INVALID
                    );
                }
                return response;
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                if (!isRetryable(exception.getStatusCode()) || attempt == maxAttempts) {
                    ErrorCode errorCode = isRetryable(exception.getStatusCode())
                            ? ErrorCode.AI_SERVICE_UNAVAILABLE
                            : ErrorCode.AI_REQUEST_REJECTED;
                    throw failure(path, exception, errorCode);
                }
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
                if (attempt == maxAttempts) {
                    throw failure(path, exception, ErrorCode.AI_SERVICE_UNAVAILABLE);
                }
            } catch (RestClientException exception) {
                throw failure(path, exception, ErrorCode.AI_RESPONSE_INVALID);
            } catch (RuntimeException exception) {
                if (exception instanceof ApiException apiException) {
                    throw apiException;
                }
                throw failure(path, exception, ErrorCode.AI_RESPONSE_INVALID);
            }
        }
        throw failure(path, lastFailure, ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.value() == 408 || status.value() == 429 || status.is5xxServerError();
    }

    private ApiException failure(String path, Throwable cause, ErrorCode errorCode) {
        String upstreamStatus = cause instanceof RestClientResponseException responseException
                ? Integer.toString(responseException.getStatusCode().value())
                : "none";
        String upstreamBody = cause instanceof RestClientResponseException responseException
                ? responseBodyPreview(responseException)
                : "none";
        String causeType = cause == null ? "unknown" : cause.getClass().getSimpleName();
        log.warn(
                "AI request failed: path={}, upstreamStatus={}, upstreamBody={}, cause={}, errorCode={}",
                path,
                upstreamStatus,
                upstreamBody,
                causeType,
                errorCode
        );
        return new ApiException(
                errorCode,
                errorCode.defaultMessage(),
                cause
        );
    }

    private static RestClient createRestClient(AiHttpProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        String baseUrl = requireSetting(properties.getBaseUrl(), "AI_BASE_URL");
        Duration connectTimeout = requirePositiveDuration(
                properties.getConnectTimeout(),
                "AI_CONNECT_TIMEOUT"
        );
        Duration readTimeout = requirePositiveDuration(
                properties.getReadTimeout(),
                "AI_READ_TIMEOUT"
        );
        if (properties.getMaxAttempts() < 1) {
            throw new IllegalStateException("AI_MAX_ATTEMPTS must be at least 1");
        }
        validatePaths(properties.getPaths());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);
        return builder.build();
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private String serializeRequest(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize AI request", exception);
        }
    }

    private void applyAuthorizationHeader(org.springframework.http.HttpHeaders headers, String explicitBearerToken) {
        String token = explicitBearerToken != null ? explicitBearerToken : currentBearerToken();
        if (token != null) {
            headers.setBearerAuth(token);
        }
    }

    private String currentBearerToken() {
        try {
            var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
            if (context == null) return null;
            var auth = context.getAuthentication();
            if (auth == null) return null;
            Object principal = auth.getPrincipal();
            if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                return jwt.getTokenValue();
            }
            Object credentials = auth.getCredentials();
            if (credentials instanceof String s && !s.isBlank()) {
                return s;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String responseBodyPreview(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= 1_000) {
            return normalized;
        }
        return normalized.substring(0, 1_000) + "...";
    }

    private static String requireSetting(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    settingName + " must be configured when AI_PROVIDER=http"
            );
        }
        return value.trim();
    }

    private static Duration requirePositiveDuration(Duration value, String settingName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(settingName + " must be a positive duration");
        }
        return value;
    }

    private static void validatePaths(AiHttpProperties.Paths paths) {
        Objects.requireNonNull(paths, "AI paths must not be null");
        requirePath(paths.getClosingSales(), "AI_CLOSING_SALES_PATH");
        requirePath(paths.getTomorrowVisitors(), "AI_TOMORROW_VISITORS_PATH");
        requirePath(paths.getDailyConsulting(), "AI_DAILY_CONSULTING_PATH");
        requirePath(paths.getChat(), "AI_CHAT_PATH");
        requirePath(paths.getMarketing(), "AI_MARKETING_PATH");
        requirePath(paths.getInstagramPublish(), "AI_INSTAGRAM_PUBLISH_PATH");
    }

    private static String requirePath(String value, String settingName) {
        String path = requireSetting(value, settingName);
        if (!path.startsWith("/")) {
            throw new IllegalStateException(settingName + " must start with '/'");
        }
        return path;
    }

    private record ClosingSalesForecastWireResponse(
            Long storeId,
            LocalDate businessDate,
            String currency,
            Long observedSalesAmount,
            Long forecastClosingSalesAmount,
            String model,
            Integer sampleDays,
            LocalDateTime generatedAt
    ) {
    }

    private record TomorrowVisitorsForecastWireResponse(
            Long storeId,
            LocalDate targetDate,
            Long expectedVisitors,
            String model,
            Integer sampleDays,
            LocalDateTime generatedAt
    ) {
    }

    private record ConsultingGenerationWireResponse(
            String title,
            LocalDate targetDate,
            String summary,
            String content,
            List<String> chatInsights,
            List<ConsultingMetricWireResponse> keyMetrics,
            List<String> externalFactors,
            List<ConsultingCauseWireResponse> estimatedCauses,
            List<ConsultingRecommendationWireResponse> recommendations,
            List<String> warnings,
            List<AiToolExecutionWireResponse> usedTools,
            List<AiCitationWireResponse> citations,
            String model
    ) {
    }

    private record ChatGenerationWireResponse(
            String answer,
            List<AiToolExecutionWireResponse> usedTools,
            List<AiCitationWireResponse> citations,
            String model,
            String finishReason
    ) {
    }

    private record MarketingGenerationWireResponse(
            String content,
            String model
    ) {
    }

    private record AiToolExecutionWireResponse(
            String toolName,
            String status,
            Map<String, Object> arguments,
            String resultSummary,
            Long latencyMs
    ) {
    }

    private record AiCitationWireResponse(String title, String url, String organization) {
    }

    private record ConsultingMetricWireResponse(
            String metricName,
            Double currentValue,
            Double previousValue,
            Double changeRate,
            String unit
    ) {
    }

    private record ConsultingCauseWireResponse(
            String title,
            String description,
            Double confidence,
            List<String> evidence
    ) {
    }

    private record ConsultingRecommendationWireResponse(
            String priority,
            String title,
            String description,
            String expectedEffect
    ) {
    }
}
