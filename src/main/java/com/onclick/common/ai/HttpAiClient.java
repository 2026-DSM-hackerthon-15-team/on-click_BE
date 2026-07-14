package com.onclick.common.ai;

import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
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

    private final RestClient restClient;
    private final AiHttpProperties properties;

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
    public ConsultingGenerationResult generateConsulting(ConsultingGenerationRequest request) {
        String path = properties.getPaths().getConsulting();
        return mapResponse(path, () -> {
            ConsultingGenerationWireResponse response = post(
                    path,
                    request,
                    ConsultingGenerationWireResponse.class
            );
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
            @JsonAlias("summary") String content
    ) {
    }

    private record ChatGenerationWireResponse(@JsonAlias("content") String answer) {
    }

    private record MarketingGenerationWireResponse(
            @JsonAlias({"caption", "answer"}) String content
    ) {
    }
}
