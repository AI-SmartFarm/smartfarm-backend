package com.smartfarm.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ai.service")
public record AiServiceProperties(
        String baseUrl,
        @DefaultValue("5000") long connectTimeoutMs,
        @DefaultValue("30000") long readTimeoutMs) {
}
