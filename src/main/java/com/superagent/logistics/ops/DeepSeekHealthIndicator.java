package com.superagent.logistics.ops;

import com.superagent.logistics.config.DeepSeekProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component("deepSeek")
public class DeepSeekHealthIndicator implements HealthIndicator {

    private final DeepSeekProperties properties;

    public DeepSeekHealthIndicator(DeepSeekProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("model", properties.getModel())
                    .build();
        }
        boolean keyFileConfigured = properties.getApiKeyFile() != null && !properties.getApiKeyFile().isBlank();
        boolean keyFileReadable = keyFileConfigured && Files.isReadable(Path.of(properties.getApiKeyFile()));
        Health.Builder builder = keyFileReadable ? Health.up() : Health.down();
        return builder
                .withDetail("enabled", true)
                .withDetail("baseUrl", properties.getBaseUrl())
                .withDetail("model", properties.getModel())
                .withDetail("apiKeyFileConfigured", keyFileConfigured)
                .withDetail("apiKeyFileReadable", keyFileReadable)
                .build();
    }
}
