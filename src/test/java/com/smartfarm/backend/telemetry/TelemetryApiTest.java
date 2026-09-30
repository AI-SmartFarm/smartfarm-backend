package com.smartfarm.backend.telemetry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.smartfarm.backend.auth.JwtTokenService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 토큰 발급(API-008) → 텔레메트리 전송(API-001)을 실제 필터·DB와 함께 확인한다.
 * 개발용 기기(SIM001)와 섞이지 않게 테스트 전용 농장·기기를 등록한다.
 */
@SpringBootTest(properties = {
		"seed.farm-id=test-farm",
		"seed.gateway-id=TEST-GW",
		"seed.gateway-secret=test-gateway-secret" })
@AutoConfigureMockMvc
class TelemetryApiTest {

	private static final String TELEMETRY = """
			{
			  "farmId": "%s",
			  "timestampUtc": "2026-10-01T05:30:00.000Z",
			  "simTimeUtc": "2025-01-01T09:16:00.000Z",
			  "weather": "Clear",
			  "sensors": { "airTempC": 21.4, "airHumidityPct": 63, "soilMoisturePct": 55, "co2Ppm": 540, "o2Ppm": 209300,
			    "lightLux": 18000, "tempAvailable": true, "humidityAvailable": true, "soilAvailable": true,
			    "co2Available": true, "o2Available": true, "lightAvailable": true },
			  "actuators": { "waterPump": true, "circFan": false, "pumpCount": 2 },
			  "crop": { "species": "Tomato", "stage": "Vegetative", "growthProgress": 0.35 },
			  "commandDelivery": "polling"
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private JwtTokenService tokenService;

	private String issueToken() throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"gatewayId\": \"TEST-GW\", \"secret\": \"test-gateway-secret\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.farmId").value("test-farm"))
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("accessToken").asString();
	}

	@Test
	void 토큰을_받아_텔레메트리를_보내면_200을_돌려준다() throws Exception {
		mockMvc.perform(post("/api/v1/farms/test-farm/telemetry")
						.header("Authorization", "Bearer " + issueToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(TELEMETRY.formatted("test-farm")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true));
	}

	@Test
	void 비밀값이_틀리면_토큰을_주지_않는다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"gatewayId\": \"TEST-GW\", \"secret\": \"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid credentials"));
	}

	@Test
	void 토큰_없이_보내면_401() throws Exception {
		mockMvc.perform(post("/api/v1/farms/test-farm/telemetry")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TELEMETRY.formatted("test-farm")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid or expired access token"));
	}

	@Test
	void 토큰의_농장과_URL의_농장이_다르면_403() throws Exception {
		mockMvc.perform(post("/api/v1/farms/other-farm/telemetry")
						.header("Authorization", "Bearer " + issueToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(TELEMETRY.formatted("other-farm")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("farm access denied"));
	}

	@Test
	void 등록되지_않은_기기의_토큰은_403() throws Exception {
		String token = tokenService.issue("UNKNOWN-GW", "test-farm");

		mockMvc.perform(post("/api/v1/farms/test-farm/telemetry")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(TELEMETRY.formatted("test-farm")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("gateway inactive"));
	}

	@Test
	void 본문의_농장이_URL과_다르면_400() throws Exception {
		mockMvc.perform(post("/api/v1/farms/test-farm/telemetry")
						.header("Authorization", "Bearer " + issueToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(TELEMETRY.formatted("other-farm")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("farmId mismatch"));
	}

	@Test
	void 기기용이_아닌_경로는_토큰을_검사하지_않는다() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
