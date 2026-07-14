package com.onclick.common.ai;

import java.net.http.HttpClient;
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
import com.onclick.common.ai.dto.TomorrowVisitorsForecastRequest;
import com.onclick.common.ai.dto.TomorrowVisitorsForecastResult;
import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

    @Autowired
    public HttpAiClient(AiHttpProperties properties) {
        this(properties, createRestClient(properties));
    }

    HttpAiClient(AiHttpProperties properties, RestClient restClient) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
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
        if (!request.targetDate().equals(response.targetDate())) {
            throw new IllegalArgumentException("targetDate does not match the request");
        }
        requireText(response.summary(), "summary");
        requireText(response.model(), "model");
        requireList(response.chatInsights(), "chatInsights");
        requireList(response.externalFactors(), "externalFactors");
        requireList(response.warnings(), "warnings");
        validateMetrics(requireList(response.keyMetrics(), "keyMetrics"));
        validateCauses(requireList(response.estimatedCauses(), "estimatedCauses"));
        validateRecommendations(requireList(response.recommendations(), "recommendations"));
        validateToolExecutions(requireList(response.usedTools(), "usedTools"));
        validateCitations(requireList(response.citations(), "citations"));
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
                        "AI request started: path={}, attempt={}/{}",
                        path,
                        attempt,
                        maxAttempts
                );
                T response = restClient.post()
                        .uri(path)
                        .body(request)
                        .retrieve()
                        .body(responseType);
                if (response == null) {
                    throw failure(
                            path,
                            new IllegalStateException("AI response body must not be null"),
                            ErrorCode.AI_RESPONSE_INVALID
                    );
                }
                log.info(
                        "AI request succeeded: path={}, attempt={}/{}",
                        path,
                        attempt,
                        maxAttempts
                );
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
        String causeType = cause == null ? "unknown" : cause.getClass().getSimpleName();
        log.warn(
                "AI request failed: path={}, upstreamStatus={}, cause={}, errorCode={}",
                path,
                upstreamStatus,
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
