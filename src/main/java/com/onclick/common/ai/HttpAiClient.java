package com.onclick.common.ai;

import java.net.http.HttpClient;
import java.util.Objects;

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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "http")
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
        return post(properties.getPaths().getClosingSales(), request, ClosingSalesForecastResult.class);
    }

    @Override
    public TomorrowVisitorsForecastResult forecastTomorrowVisitors(TomorrowVisitorsForecastRequest request) {
        return post(properties.getPaths().getTomorrowVisitors(), request, TomorrowVisitorsForecastResult.class);
    }

    @Override
    public ConsultingGenerationResult generateConsulting(ConsultingGenerationRequest request) {
        return post(properties.getPaths().getConsulting(), request, ConsultingGenerationResult.class);
    }

    @Override
    public ChatGenerationResult generateChatReply(ChatGenerationRequest request) {
        return post(properties.getPaths().getChat(), request, ChatGenerationResult.class);
    }

    @Override
    public MarketingGenerationResult generateMarketing(MarketingGenerationRequest request) {
        return post(properties.getPaths().getMarketing(), request, MarketingGenerationResult.class);
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        Objects.requireNonNull(request, "request must not be null");
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T response = restClient.post()
                        .uri(path)
                        .body(request)
                        .retrieve()
                        .body(responseType);
                if (response == null) {
                    throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE);
                }
                return response;
            } catch (RestClientResponseException exception) {
                lastFailure = exception;
                if (!isRetryable(exception.getStatusCode()) || attempt == maxAttempts) {
                    throw unavailable(exception);
                }
            } catch (RestClientException exception) {
                lastFailure = exception;
                if (attempt == maxAttempts) {
                    throw unavailable(exception);
                }
            } catch (RuntimeException exception) {
                if (exception instanceof ApiException apiException) {
                    throw apiException;
                }
                throw unavailable(exception);
            }
        }
        throw unavailable(lastFailure);
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private ApiException unavailable(Throwable cause) {
        return new ApiException(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                ErrorCode.AI_SERVICE_UNAVAILABLE.defaultMessage(),
                cause
        );
    }

    private static RestClient createRestClient(AiHttpProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory);
        if (properties.getInternalApiKey() != null && !properties.getInternalApiKey().isBlank()) {
            builder.defaultHeader(
                    properties.getInternalApiKeyHeader(),
                    properties.getInternalApiKey()
            );
        }
        return builder.build();
    }
}
