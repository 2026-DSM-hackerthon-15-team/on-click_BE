package com.onclick.global.config;

import java.time.Clock;
import java.util.Optional;

import com.onclick.common.time.KoreanTime;
import com.onclick.global.config.properties.MediaProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
@EnableScheduling
@EnableConfigurationProperties(MediaProperties.class)
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.system(KoreanTime.ZONE);
    }

    @Bean
    DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(KoreanTime.now(clock));
    }
}
