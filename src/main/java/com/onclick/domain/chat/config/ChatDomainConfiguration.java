package com.onclick.domain.chat.config;

import com.onclick.domain.chat.generation.ChatGenerationPort;
import com.onclick.domain.chat.generation.DeterministicMockChatGenerationAdapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatProcessingProperties.class)
public class ChatDomainConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChatGenerationPort.class)
    ChatGenerationPort mockChatGenerationPort() {
        return new DeterministicMockChatGenerationAdapter();
    }
}
