package com.smartfarm.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** apiKey가 비어 있지 않으면 모든 AI 호출에 X-API-Key 헤더로 보낸다. */
@ConfigurationProperties(prefix = "ai.service")
public record AiServiceProperties(
		String baseUrl,
		String apiKey,
		@DefaultValue("5000") long connectTimeoutMs,
		@DefaultValue("30000") long readTimeoutMs) {
}
