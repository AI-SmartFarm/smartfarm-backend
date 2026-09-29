package com.smartfarm.backend.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class AiClientConfig {

	@Bean
	RestClient aiRestClient(AiServiceProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}
}
