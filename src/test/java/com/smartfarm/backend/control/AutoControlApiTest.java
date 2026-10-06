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
import org.springframework.test.web.servlet.ResultActions;

import com.smartfarm.backend.auth.JwtTokenService;
import com.smartfarm.backend.command.ControlCommandRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * 텔레메트리(API-001) → 자동제어 판단 → 명령 조회·결과 보고(API-003)를 실제 필터·DB와 함께 확인한다.
 * 쿨다운을 0으로 둬서 한 테스트 안에서 켜기와 끄기를 이어서 확인한다. 쿨다운 자체는 AutoControlCooldownTest가 본다.
 */
@SpringBootTest(properties = {
		"seed.farm-id=control-farm",
		"seed.gateway-id=CONTROL-GW",
		"seed.gateway-secret=test-gateway-secret",
		"control.command.cooldown-seconds=0" })
@AutoConfigureMockMvc
class AutoControlApiTest {

	private static final String FARM = "control-farm";
	private static final String COMMANDS = "/api/v1/farms/" + FARM + "/commands";

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
		// 로컬 DB는 테스트 사이에 유지되므로 이 농장의 이전 명령을 지운다.
		commandRepository.deleteAll(commandRepository.findAll().stream()
				.filter(c -> FARM.equals(c.getFarmId())).toList());
		bearer = "Bearer " + tokenService.issue("CONTROL-GW", FARM);
	}

	private void telemetry(String species, double temp, double soil, boolean fanOn, boolean pumpOn) throws Exception {
		String body = """
				{
				  "farmId": "%s",
				  "timestampUtc": "%s",
				  "sensors": { "airTempC": %s, "soilMoisturePct": %s, "tempAvailable": true, "soilAvailable": true },
				  "actuators": { "circFan": %s, "waterPump": %s },
				  "crop": { "species": "%s", "stage": "Vegetative" }
				}
				""".formatted(FARM, Instant.now(), temp, soil, fanOn, pumpOn, species);
		mockMvc.perform(post("/api/v1/farms/" + FARM + "/telemetry")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}

	private ResultActions poll() throws Exception {
		return mockMvc.perform(get(COMMANDS).header("Authorization", bearer)).andExpect(status().isOk());
	}

	private String onlyCommandId() throws Exception {
		String body = poll().andExpect(jsonPath("$.commands.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("commands").get(0).get("id").asString();
	}

	private void ack(String id, String status) throws Exception {
		mockMvc.perform(post(COMMANDS + "/ack")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\": \"%s\", \"status\": \"%s\"}".formatted(id, status)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true));
	}

	@Test
	void 고온이면_순환팬을_켜는_명령이_생기고_결과_보고_전까지_계속_내려준다() throws Exception {
		telemetry("Tomato", 28.0, 40.0, false, false);

		poll().andExpect(jsonPath("$.commands.length()").value(1))
				.andExpect(jsonPath("$.commands[0].actuator").value("circFan"))
				.andExpect(jsonPath("$.commands[0].action").value("on"))
				.andExpect(jsonPath("$.commands[0].reason").value("TEMP_HIGH"));
		String id = onlyCommandId();

		ack(id, "applied");

		poll().andExpect(jsonPath("$.commands.length()").value(0));
	}

	@Test
	void 결과를_기다리는_동안에는_같은_장치에_명령을_또_만들지_않는다() throws Exception {
		telemetry("Tomato", 28.0, 40.0, false, false);
		telemetry("Tomato", 28.5, 40.0, false, false);

		poll().andExpect(jsonPath("$.commands.length()").value(1));
	}

	@Test
	void 두_기준_사이에서는_명령이_없고_끄는_기준에_닿으면_끈다() throws Exception {
		telemetry("Tomato", 26.0, 40.0, true, false);
		poll().andExpect(jsonPath("$.commands.length()").value(0));

		telemetry("Tomato", 24.5, 40.0, true, false);

		poll().andExpect(jsonPath("$.commands.length()").value(1))
				.andExpect(jsonPath("$.commands[0].actuator").value("circFan"))
				.andExpect(jsonPath("$.commands[0].action").value("off"))
				.andExpect(jsonPath("$.commands[0].reason").value("TEMP_NORMAL"));
	}

	@Test
	void 토양_수분이_낮으면_관수를_켜고_충분해지면_끈다() throws Exception {
		telemetry("Tomato", 24.0, 25.0, false, false);
		poll().andExpect(jsonPath("$.commands[0].actuator").value("waterPump"))
				.andExpect(jsonPath("$.commands[0].action").value("on"))
				.andExpect(jsonPath("$.commands[0].reason").value("SOIL_LOW"));
		ack(onlyCommandId(), "applied");

		telemetry("Tomato", 24.0, 55.0, false, true);

		poll().andExpect(jsonPath("$.commands.length()").value(1))
				.andExpect(jsonPath("$.commands[0].action").value("off"))
				.andExpect(jsonPath("$.commands[0].reason").value("SOIL_ENOUGH"));
	}

	@Test
	void 기준값이_없는_작물은_제어하지_않는다() throws Exception {
		telemetry("Pepper", 35.0, 5.0, false, false);

		poll().andExpect(jsonPath("$.commands.length()").value(0));
	}

	@Test
	void 명령_조회와_결과_보고는_토큰이_있어야_한다() throws Exception {
		mockMvc.perform(get(COMMANDS)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(COMMANDS + "/ack")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\": \"cmd_x\", \"status\": \"applied\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 결과_보고의_status가_틀리면_400() throws Exception {
		mockMvc.perform(post(COMMANDS + "/ack")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\": \"cmd_x\", \"status\": \"done\"}"))
				.andExpect(status().isBadRequest());
	}
}
