package com.smartfarm.backend.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.smartfarm.backend.auth.JwtTokenService;
import com.smartfarm.backend.command.ControlCommand;
import com.smartfarm.backend.command.ControlCommandRepository;
import com.smartfarm.backend.common.ClockConfig;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사진 저장 정책(주기 사진 제한, 정상 사진 정리)과 병해 자동 대응(F-04)을 실제 필터·DB, 가짜 AI 서버로 확인한다.
 * 진단은 동기로 돌려(diagnosis.async=false) 요청이 끝나면 결과를 바로 본다.
 */
@SpringBootTest(properties = {
		"seed.farm-id=resp-farm",
		"seed.gateway-id=RESP-GW",
		"seed.gateway-secret=test-gateway-secret",
		"diagnosis.async=false",
		"control.command.cooldown-seconds=0",
		"image.routine-interval-seconds=3600" })
@AutoConfigureMockMvc
class DiagnosisResponseFlowTest {

	private static final String FARM = "resp-farm";

	private static final String LEAF_MOLD = """
			{"result": "detected", "crop": "tomato",
			 "detections": [{"class": "tomato_disease18", "confidence": 0.91, "bbox": [1.0, 2.0, 3.0, 4.0],
			   "severity": {"level": "초기", "risk_code": 1, "confidence": 0.7, "low_confidence": false}}]}
			""";
	private static final String LEAF_MOLD_WITH_GUIDE = """
			{"result": "detected", "crop": "tomato",
			 "detections": [{"class": "tomato_disease18", "confidence": 0.91, "bbox": [1.0, 2.0, 3.0, 4.0],
			   "diagnosis": {"name_kr": "토마토잎곰팡이병", "prevention_principles": ["습도를 낮춘다", " ", "병든 잎을 없앤다"]}}]}
			""";
	private static final String TYLCV = """
			{"result": "detected", "crop": "tomato",
			 "detections": [{"class": "tomato_disease19", "confidence": 0.88, "bbox": [1.0, 2.0, 3.0, 4.0]}]}
			""";
	private static final String HEALTHY = """
			{"result": "no_detection", "crop": "tomato", "detections": []}
			""";

	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };

	private static final AtomicReference<String> aiResponse = new AtomicReference<>(HEALTHY);
	private static final AtomicInteger aiCalls = new AtomicInteger();
	private static final HttpServer AI = startAi();
	private static final Path STORAGE = tempDir();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private JwtTokenService tokenService;

	@Autowired
	private ControlCommandRepository commandRepository;

	private String bearer;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("ai.service.base-url", () -> "http://localhost:" + AI.getAddress().getPort());
		registry.add("image.storage-dir", STORAGE::toString);
	}

	private static HttpServer startAi() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/diagnose", exchange -> {
				aiCalls.incrementAndGet();
				exchange.getRequestBody().readAllBytes();
				byte[] body = aiResponse.get().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path tempDir() {
		try {
			return Files.createTempDirectory("smartfarm-resp-images");
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@AfterAll
	static void stopAi() {
		AI.stop(0);
	}

	@BeforeEach
	void setUp() {
		commandRepository.deleteAll(commandRepository.findAll().stream()
				.filter(c -> FARM.equals(c.getFarmId())).toList());
		aiResponse.set(HEALTHY);
		aiCalls.set(0);
		bearer = "Bearer " + tokenService.issue("RESP-GW", FARM);
	}

	/** 카메라 ID는 테스트마다 달리 줘서, 로컬 DB에 남은 이전 실행의 사진이 주기 제한에 걸리지 않게 한다. */
	private static String camera(String name) {
		return "cam-" + name + "-" + System.nanoTime();
	}

	private JsonNode sendImage(String trigger, String cameraId) throws Exception {
		String body = """
				{"farmId": "%s", "timestampUtc": "%s", "format": "jpg", "width": 640, "height": 480,
				 "imageBase64": "%s", "source": "dataset-photo", "trigger": "%s", "species": "Tomato",
				 "cameraId": "%s", "cellX": 0, "cellZ": 3}
				""".formatted(FARM, Instant.now(), Base64.getEncoder().encodeToString(JPEG), trigger, cameraId);
		String response = mockMvc.perform(post("/api/v1/farms/" + FARM + "/images")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response);
	}

	private void telemetry(double temp, boolean fanOn) throws Exception {
		String body = """
				{"farmId": "%s", "timestampUtc": "%s",
				 "sensors": { "airTempC": %s, "soilMoisturePct": 40, "tempAvailable": true, "soilAvailable": true },
				 "actuators": { "circFan": %s, "waterPump": false },
				 "crop": { "species": "Tomato" }}
				""".formatted(FARM, Instant.now(), temp, fanOn);
		mockMvc.perform(post("/api/v1/farms/" + FARM + "/telemetry")
						.header("Authorization", bearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}

	private JsonNode latestDiagnoses(int size) throws Exception {
		String body = mockMvc.perform(get("/api/v1/farms/" + FARM + "/diagnoses?size=" + size))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("diagnoses");
	}

	private List<ControlCommand> commands() {
		return commandRepository.findAll().stream().filter(c -> FARM.equals(c.getFarmId())).toList();
	}

	@Test
	void 주기_사진은_카메라별로_간격_안에_한_장만_저장하고_진단한다() throws Exception {
		String cam = camera("a");

		JsonNode first = sendImage("routine", cam);
		JsonNode second = sendImage("routine", cam);

		assertThat(first.get("ok").asBoolean()).isTrue();
		assertThat(first.get("file").isNull()).isFalse();
		assertThat(second.get("ok").asBoolean()).isTrue();
		assertThat(second.get("file").isNull()).isTrue();
		assertThat(aiCalls.get()).isEqualTo(1);
	}

	@Test
	void 감염_순간과_다른_카메라의_사진은_주기_제한과_상관없이_저장한다() throws Exception {
		String cam = camera("b");
		sendImage("routine", cam);

		assertThat(sendImage("pest", cam).get("file").isNull()).isFalse();
		assertThat(sendImage("routine", camera("c")).get("file").isNull()).isFalse();
		assertThat(aiCalls.get()).isEqualTo(3);
	}

	@Test
	void 정상_사진은_카메라별_최신_한_장만_파일을_남긴다() throws Exception {
		String cam = camera("d");
		sendImage("request", cam);
		sendImage("request", cam);

		JsonNode diagnoses = latestDiagnoses(2);
		assertThat(diagnoses.get(0).get("imageUrl").isNull()).isFalse();
		assertThat(diagnoses.get(1).get("imageUrl").isNull()).isTrue();
		mockMvc.perform(get(diagnoses.get(0).get("imageUrl").asString())).andExpect(status().isOk());
	}

	@Test
	void 병이_감지된_사진은_뒤에_정상_사진이_와도_지우지_않는다() throws Exception {
		String cam = camera("e");
		aiResponse.set(LEAF_MOLD);
		sendImage("pest", cam);
		aiResponse.set(HEALTHY);
		sendImage("request", cam);

		JsonNode infected = latestDiagnoses(2).get(1);
		assertThat(infected.get("infected").asBoolean()).isTrue();
		mockMvc.perform(get(infected.get("imageUrl").asString())).andExpect(status().isOk());
	}

	@Test
	void 잎곰팡이병이면_순환팬을_60분_켜는_명령을_만들고_진단에_연결한다() throws Exception {
		aiResponse.set(LEAF_MOLD);
		sendImage("pest", camera("f"));

		JsonNode diagnosis = latestDiagnoses(1).get(0);
		assertThat(diagnosis.get("responses")).hasSize(1);
		JsonNode response = diagnosis.get("responses").get(0);
		assertThat(response.get("actuator").asString()).isEqualTo("circFan");
		assertThat(response.get("action").asString()).isEqualTo("on");
		assertThat(response.get("durationMinutes").asInt()).isEqualTo(60);
		assertThat(diagnosis.get("guide").asString()).contains("환기");

		ControlCommand command = commands().getFirst();
		assertThat(command.getReason()).isEqualTo("PEST_RESPONSE");
		assertThat(command.getSource()).isEqualTo(ControlCommand.SOURCE_AI);
	}

	@Test
	void 안내_문구는_AI의_예방_방제_원칙을_쓰고_장치_대응은_그대로_한다() throws Exception {
		aiResponse.set(LEAF_MOLD_WITH_GUIDE);
		sendImage("pest", camera("w"));

		JsonNode diagnosis = latestDiagnoses(1).get(0);
		assertThat(diagnosis.get("guide").asString()).isEqualTo("습도를 낮춘다\n병든 잎을 없앤다");
		assertThat(diagnosis.get("responses")).hasSize(1);
	}

	@Test
	void 유지_중에_다시_감염이_진단되면_명령을_또_만들지_않는다() throws Exception {
		aiResponse.set(LEAF_MOLD);
		sendImage("pest", camera("g"));
		sendImage("pest", camera("h"));

		assertThat(commands()).hasSize(1);
	}

	@Test
	void 대응_규칙이_알림뿐인_병은_장치를_움직이지_않고_안내만_준다() throws Exception {
		aiResponse.set(TYLCV);
		sendImage("pest", camera("i"));

		JsonNode diagnosis = latestDiagnoses(1).get(0);
		assertThat(diagnosis.get("diseaseCode").asString()).isEqualTo("tomato-B");
		assertThat(diagnosis.get("responses")).isEmpty();
		assertThat(diagnosis.get("guide").asString()).contains("제거");
		assertThat(commands()).isEmpty();
	}

	@Test
	void 병해_대응_유지_중에는_온도가_낮아도_팬을_끄지_않는다() throws Exception {
		LocalDateTime now = LocalDateTime.now(ClockConfig.ZONE);
		commandRepository.save(ControlCommand.pestResponse("cmd_holdnow0001", FARM, "circFan", 1L, 3600, now,
				now.plusSeconds(60)));

		telemetry(20.0, true);

		assertThat(commands()).hasSize(1);
	}

	@Test
	void 유지_시간이_끝나면_켜는_기준보다_낮을_때_팬을_끈다() throws Exception {
		LocalDateTime twoHoursAgo = LocalDateTime.now(ClockConfig.ZONE).minusHours(2);
		commandRepository.save(ControlCommand.pestResponse("cmd_holdend0001", FARM, "circFan", 1L, 3600, twoHoursAgo,
				twoHoursAgo.plusSeconds(60)));

		// 26℃는 두 기준(27/25) 사이라 평소에는 "유지"지만, 병해 대응이 끝났으므로 끈다.
		telemetry(26.0, true);

		ControlCommand off = commands().stream().filter(c -> "off".equals(c.getAction())).findFirst().orElseThrow();
		assertThat(off.getActuator()).isEqualTo("circFan");
		assertThat(off.getReason()).isEqualTo("PEST_RESPONSE_END");
	}
}
