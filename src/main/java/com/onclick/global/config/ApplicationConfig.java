package com.onclick.global.config;

import java.time.Clock;
import java.time.ZoneId;

import com.onclick.global.config.properties.InstagramProperties;
import com.onclick.global.config.properties.MediaProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({MediaProperties.class, InstagramProperties.class})
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
