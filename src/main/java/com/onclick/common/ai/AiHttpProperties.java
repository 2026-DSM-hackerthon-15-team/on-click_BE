package com.onclick.common.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.http")
public class AiHttpProperties {

    private String baseUrl = "http://localhost:8000";
    private String internalApiKey = "";
    private String internalApiKeyHeader = "X-Internal-Api-Key";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(10);
    private int maxAttempts = 2;
    private final Paths paths = new Paths();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    public String getInternalApiKeyHeader() {
        return internalApiKeyHeader;
    }

    public void setInternalApiKeyHeader(String internalApiKeyHeader) {
        this.internalApiKeyHeader = internalApiKeyHeader;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Paths getPaths() {
        return paths;
    }

    public static class Paths {

        private String closingSales = "/ai/forecasts/closing-sales";
        private String tomorrowVisitors = "/ai/forecasts/tomorrow-visitors";
        private String consulting = "/ai/consultings";
        private String chat = "/ai/chat";
        private String marketing = "/ai/marketing/generations";

        public String getClosingSales() {
            return closingSales;
        }

        public void setClosingSales(String closingSales) {
            this.closingSales = closingSales;
        }

        public String getTomorrowVisitors() {
            return tomorrowVisitors;
        }

        public void setTomorrowVisitors(String tomorrowVisitors) {
            this.tomorrowVisitors = tomorrowVisitors;
        }

        public String getConsulting() {
            return consulting;
        }

        public void setConsulting(String consulting) {
            this.consulting = consulting;
        }

        public String getChat() {
            return chat;
        }

        public void setChat(String chat) {
            this.chat = chat;
        }

        public String getMarketing() {
            return marketing;
        }

        public void setMarketing(String marketing) {
            this.marketing = marketing;
        }
    }
}
