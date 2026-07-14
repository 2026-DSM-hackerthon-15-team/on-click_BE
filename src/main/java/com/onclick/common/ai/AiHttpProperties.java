package com.onclick.common.ai;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.http")
@Getter
@Setter
public class AiHttpProperties {

    private String baseUrl = "http://localhost:8000";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(60);
    private int maxAttempts = 2;
    private final Paths paths = new Paths();

    @Getter
    @Setter
    public static class Paths {

        private String closingSales = "/ai/forecasts/closing-sales";
        private String tomorrowVisitors = "/ai/forecasts/tomorrow-visitors";
        private String dailyConsulting = "/ai/consultings/daily";
        private String chat = "/ai/chat";
        private String marketing = "/ai/marketings/copy";

    }
}
