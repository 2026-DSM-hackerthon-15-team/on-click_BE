package com.onclick.common.ai;

import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonAlias;
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
import com.onclick.common.time.KoreanTime;
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
            return new ClosingSalesForecastResult(
                    requireNonNegative(response.expectedClosingSales(), "expectedClosingSales"),
                    toKoreanDateTime(response.generatedAt())
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
            return new TomorrowVisitorsForecastResult(
                    requireNonNegative(response.expectedVisitors(), "expectedVisitors"),
                    toKoreanDateTime(response.generatedAt())
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
        return mapResponse(path, () -> {
            MarketingGenerationWireResponse response = post(
                    path,
                    request,
                    MarketingGenerationWireResponse.class
            );
            return new MarketingGenerationResult(response.content());
        });
    }

    private LocalDateTime toKoreanDateTime(Instant instant) {
        if (instant == null) {
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        return KoreanTime.fromInstant(instant);
    }

    private long requireNonNegative(Long value, String field) {
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
            throw unavailable(path, exception);
        }
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        Objects.requireNonNull(request, "request must not be null");
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        RestClientException lastFailure = null;
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
                    throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE);
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
                    throw unavailable(path, exception);
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
                if (attempt == maxAttempts) {
                    throw unavailable(path, exception);
                }
            } catch (RuntimeException exception) {
                if (exception instanceof ApiException apiException) {
                    throw apiException;
                }
                throw unavailable(path, exception);
            }
        }
        throw unavailable(path, lastFailure);
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private ApiException unavailable(String path, Throwable cause) {
        String upstreamStatus = cause instanceof RestClientResponseException responseException
                ? Integer.toString(responseException.getStatusCode().value())
                : "none";
        String causeType = cause == null ? "unknown" : cause.getClass().getSimpleName();
        log.warn(
                "AI request failed: path={}, upstreamStatus={}, cause={}",
                path,
                upstreamStatus,
                causeType
        );
        return new ApiException(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                ErrorCode.AI_SERVICE_UNAVAILABLE.defaultMessage(),
                cause
        );
    }

    private static RestClient createRestClient(AiHttpProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        String baseUrl = requireSetting(properties.getBaseUrl(), "AI_BASE_URL");
        String internalApiKey = requireSetting(properties.getInternalApiKey(), "AI_INTERNAL_API_KEY");
        String internalApiKeyHeader = requireSetting(
                properties.getInternalApiKeyHeader(),
                "AI_INTERNAL_API_KEY_HEADER"
        );
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(internalApiKeyHeader, internalApiKey);
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

    private record ClosingSalesForecastWireResponse(Long expectedClosingSales, Instant generatedAt) {
    }

    private record TomorrowVisitorsForecastWireResponse(Long expectedVisitors, Instant generatedAt) {
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
            @JsonAlias("content") String answer,
            List<AiToolExecutionWireResponse> usedTools,
            List<AiCitationWireResponse> citations,
            String model,
            String finishReason
    ) {
    }

    private record MarketingGenerationWireResponse(
            @JsonAlias({"caption", "answer"}) String content
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
