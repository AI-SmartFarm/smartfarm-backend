package com.smartfarm.backend.control;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.smartfarm.backend.auth.JwtTokenService;
import com.smartfarm.backend.command.ControlCommandRepository;

import tools.jackson.databind.json.JsonMapper;

/** 시뮬레이터가 명령을 거절해도(예: 펌프 고장) 다음 텔레메트리마다 같은 명령을 반복하지 않는지 확인한다. */
@SpringBootTest(properties = {
		"seed.farm-id=cooldown-farm",
		"seed.gateway-id=COOLDOWN-GW",
		"seed.gateway-secret=test-gateway-secret",
		"control.command.cooldown-seconds=3600" })
@AutoConfigureMockMvc
class AutoControlCooldownTest {

	private static final String FARM = "cooldown-farm";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private ControlCommandRepository commandRepository;

	private String bearer;

	@BeforeEach
	void setUp() {
		commandRepository.deleteAll(commandRepository.findAll().stream()
				.filter(c -> FARM.equals(c.getFarmId())).toList());
		bearer = "Bearer " + tokenService.issue("COOLDOWN-GW", FARM);
	}

	private void lowSoilTelemetry() throws Exception {
		String body = """
				{
				  "farmId": "%s",
				  "timestampUtc": "%s",
				  "sensors": { "airTempC": 24.0, "soilMoisturePct": 20.0, "tempAvailable": true, "soilAvailable": true },
				  "actuators": { "circFan": false, "waterPump": false },
				  "crop": { "species": "Tomato" }
				}
				""".formatted(FARM, Instant.now());
		mockMvc.perform(post("/api/v1/farms/" + FARM + "/telemetry")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}

	@Test
	void 거절된_직후에는_같은_명령을_다시_만들지_않는다() throws Exception {
		lowSoilTelemetry();
		String body = mockMvc.perform(get("/api/v1/farms/" + FARM + "/commands").header("Authorization", bearer))
				.andExpect(jsonPath("$.commands.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		String id = jsonMapper.readTree(body).get("commands").get(0).get("id").asString();
		mockMvc.perform(post("/api/v1/farms/" + FARM + "/commands/ack")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\": \"%s\", \"status\": \"rejected\"}".formatted(id)))
				.andExpect(status().isOk());

		lowSoilTelemetry();

		mockMvc.perform(get("/api/v1/farms/" + FARM + "/commands").header("Authorization", bearer))
				.andExpect(jsonPath("$.commands.length()").value(0));
	}
}
