package com.smartfarm.backend.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프론트·시뮬레이터 담당이 보는 Swagger 문서가 실제 API와 맞게 만들어지는지 확인한다.
 * 기기 API에만 JWT 요구가 붙고 모바일 API에는 붙지 않아야 Swagger에서 그대로 호출해 볼 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

	@Autowired
	MockMvc mockMvc;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void OpenAPI_문서에_모든_API가_있고_기기_API에만_JWT가_붙는다() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode paths = jsonMapper.readTree(body).get("paths");

		assertThat(paths.has("/api/v1/auth/token")).isTrue();
		assertThat(paths.has("/api/v1/farms/{farmId}/telemetry")).isTrue();
		assertThat(paths.has("/api/v1/farms/{farmId}/commands")).isTrue();
		assertThat(paths.has("/api/v1/farms/{farmId}/commands/ack")).isTrue();
		assertThat(paths.has("/api/v1/farms/{farmId}/images")).isTrue();
		assertThat(paths.has("/api/v1/farms/{farmId}/diagnoses")).isTrue();
		assertThat(paths.has("/api/v1/images/{imageId}/file")).isTrue();

		assertThat(paths.get("/api/v1/farms/{farmId}/telemetry").get("post").has("security")).isTrue();
		assertThat(paths.get("/api/v1/farms/{farmId}/commands").get("get").has("security")).isTrue();
		assertThat(paths.get("/api/v1/farms/{farmId}/images").get("post").has("security")).isTrue();
		assertThat(paths.get("/api/v1/farms/{farmId}/diagnoses").get("get").has("security")).isFalse();
		assertThat(paths.get("/api/v1/auth/token").get("post").has("security")).isFalse();
	}

	@Test
	void Swagger_UI가_열린다() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}
}
