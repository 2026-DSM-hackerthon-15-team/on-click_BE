package com.onclick.common.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HttpAiClientContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(AiHttpProperties.class, HttpAiClient.class)
            .withPropertyValues(
                    "app.ai.provider=http",
                    "app.ai.http.base-url=http://localhost:8000",
                    "app.ai.http.internal-api-key=test-internal-key"
            );

    @Test
    void createsHttpAiClientWhenHttpProviderIsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(HttpAiClient.class);
            assertThat(context).hasSingleBean(AiClient.class);
        });
    }
}
