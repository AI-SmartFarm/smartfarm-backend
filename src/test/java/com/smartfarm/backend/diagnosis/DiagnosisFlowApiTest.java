package com.smartfarm.backend.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
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

import com.smartfarm.backend.command.ControlCommandRepository;
import com.smartfarm.backend.image.CropImageRepository;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사진 수신(API-004) → AI 진단(API-005, 가짜 AI 서버) → 진단 이력 조회(API-008)를 실제 필터·DB와 함께 확인한다.
 * 진단을 바로 확인하려고 diagnosis.async=false로 둔다. 다른 테스트와 섞이지 않게 전용 농장·기기를 등록한다.
 */
@SpringBootTest(properties = {
		"seed.farm-id=diag-farm",
		"seed.gateway-id=DIAG-GW",
		"seed.gateway-secret=diag-gateway-secret",
		"diagnosis.async=false" })
@AutoConfigureMockMvc
class DiagnosisFlowApiTest {

	private static final String AI_DETECTED = """
			{"result": "detected", "crop": "tomato", "model_version": "rfdetr-v5_severity-v2",
			 "detections": [{"class": "tomato_disease18", "confidence": 0.93, "bbox": [120.0, 80.0, 300.0, 260.0],
			   "severity": {"level": "중기", "risk_code": 2, "confidence": 0.81, "model_valid_accuracy": 0.63,
			                "low_confidence": false},
			   "diagnosis": {"name_kr": "잎곰팡이병", "symptoms": ["잎 뒷면 곰팡이"], "prevention_principles": ["환기"]}}]}
			""";

	// JPEG로 판별되는 최소한의 바이트. 가짜 AI 서버는 사진을 해석하지 않는다.
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };

	private static final HttpServer AI = startAi();
	private static final AtomicReference<String> aiBody = new AtomicReference<>();
	private static final AtomicInteger aiCalls = new AtomicInteger();
	private static final AtomicInteger aiStatus = new AtomicInteger(200);
	private static final Path STORAGE = tempDir();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private CropImageRepository cropImageRepository;

	@Autowired
	private DiagnosisRepository diagnosisRepository;

	@Autowired
	private ControlCommandRepository commandRepository;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("ai.service.base-url", () -> "http://localhost:" + AI.getAddress().getPort());
		registry.add("ai.service.api-key", () -> "test-ai-key");
		registry.add("image.storage-dir", STORAGE::toString);
	}

	private static HttpServer startAi() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/diagnose", exchange -> {
				aiCalls.incrementAndGet();
				aiBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1)
						+ "\nX-API-Key: " + exchange.getRequestHeaders().getFirst("X-API-Key"));
				byte[] body = (aiStatus.get() == 200 ? AI_DETECTED : "{\"detail\": \"boom\"}")
						.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(aiStatus.get(), body.length);
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
			return Files.createTempDirectory("smartfarm-images");
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
	void resetAi() {
		// 병해 대응은 60분 유지되므로, 로컬 DB에 남은 이전 실행의 명령이 결과를 바꾸지 않게 지운다.
		commandRepository.deleteAll(commandRepository.findAll().stream()
				.filter(c -> "diag-farm".equals(c.getFarmId())).toList());
		aiStatus.set(200);
		aiCalls.set(0);
		aiBody.set(null);
	}

	private String token() throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"gatewayId\": \"DIAG-GW\", \"secret\": \"diag-gateway-secret\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("accessToken").asString();
	}

	private static String image(String species, byte[] bytes) {
		return """
				{"farmId": "diag-farm", "timestampUtc": "2026-10-02T05:31:02.000Z", "format": "jpg",
				 "width": 2064, "height": 1548, "imageBase64": "%s", "source": "dataset-photo", "trigger": "pest",
				 "species": "%s", "cellX": 5, "cellZ": -1, "groundTruthStage": "Flowering", "groundTruthPest": "A",
				 "groundTruthPestLabel": "tomato-A", "groundTruthPestName": "잎곰팡이병", "groundTruthPestSeverity": 0.5,
				 "datasetFile": "13_토마토_잎곰팡이병.jpg",
				 "estimate": {"estimatedStage": "Flowering", "confidence": 0.71, "method": "heuristic-v1"}}
				""".formatted(Base64.getEncoder().encodeToString(bytes), species);
	}

	private String send(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/farms/diag-farm/images")
						.header("Authorization", "Bearer " + token())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andReturn().getResponse().getContentAsString();
	}

	private JsonNode latestDiagnosis() throws Exception {
		String body = mockMvc.perform(get("/api/v1/farms/diag-farm/diagnoses?size=1"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("diagnoses").get(0);
	}

	@Test
	void 사진을_받으면_진단해서_이력으로_조회된다() throws Exception {
		String file = jsonMapper.readTree(send(image("Tomato", JPEG))).get("file").asString();
		assertThat(file).endsWith(".jpg");

		// AI에는 사진·작물·키만 가고, 평가용 정답은 가지 않는다.
		assertThat(aiCalls.get()).isEqualTo(1);
		assertThat(aiBody.get()).contains("name=\"crop\"", "tomato", "X-API-Key: test-ai-key")
				.doesNotContain("groundTruth", "tomato-A", "estimate", "잎곰팡이병");

		JsonNode d = latestDiagnosis();
		assertThat(d.get("infected").asBoolean()).isTrue();
		assertThat(d.get("diseaseCode").asString()).isEqualTo("tomato-A");
		assertThat(d.get("diseaseName").asString()).isEqualTo("잎곰팡이병");
		assertThat(d.get("severityLevel").asString()).isEqualTo("중기");
		assertThat(d.get("severityLowConfidence").asBoolean()).isFalse();
		assertThat(d.get("confidence").asDouble()).isEqualTo(0.93);
		assertThat(d.get("diagnosedAt").asString()).endsWith("+09:00");
		// 잎곰팡이병은 병해 대응 규칙에 따라 순환팬을 60분 켠다(F-04).
		assertThat(d.get("responses")).hasSize(1);
		assertThat(d.get("responses").get(0).get("actuator").asString()).isEqualTo("circFan");
		assertThat(d.get("responses").get(0).get("action").asString()).isEqualTo("on");
		assertThat(d.get("responses").get(0).get("durationMinutes").asInt()).isEqualTo(60);
		assertThat(d.get("guide").asString()).contains("환기");

		Diagnosis saved = diagnosisRepository.findById(d.get("diagnosisId").asLong()).orElseThrow();
		assertThat(saved.getModelVersion()).isEqualTo("rfdetr-v5_severity-v2");
		// MySQL은 JSON 컬럼을 공백을 넣어 다시 쓰므로 문자열이 아니라 값으로 비교한다.
		JsonNode box = jsonMapper.readTree(saved.getBoxes()).get(0);
		assertThat(box.get("width").asDouble()).isEqualTo(180.0);
		assertThat(box.get("height").asDouble()).isEqualTo(180.0);
		assertThat(cropImageRepository.findById(saved.getImageId()).orElseThrow().getGtPestLabel()).isEqualTo("tomato-A");

		mockMvc.perform(get(d.get("imageUrl").asString()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_JPEG))
				.andExpect(content().bytes(JPEG));
	}

	@Test
	void 식물이_없는_사진은_저장만_하고_진단하지_않는다() throws Exception {
		long before = diagnosisRepository.count();
		send(image("none", JPEG));

		assertThat(aiCalls.get()).isZero();
		assertThat(diagnosisRepository.count()).isEqualTo(before);
	}

	@Test
	void AI가_실패하면_사진은_받고_진단은_실패로_남긴다() throws Exception {
		aiStatus.set(500);
		long beforeHistory = latestId();
		send(image("Tomato", JPEG));

		assertThat(aiCalls.get()).isEqualTo(1);
		Diagnosis failed = diagnosisRepository.findAll().stream()
				.max((a, b) -> Long.compare(a.getDiagnosisId(), b.getDiagnosisId())).orElseThrow();
		assertThat(failed.getDiagnosedAt()).isNull();
		assertThat(failed.isInfected()).isFalse();
		// 실패한 진단은 이력에 나오지 않는다.
		assertThat(latestId()).isEqualTo(beforeHistory);
	}

	private long latestId() throws Exception {
		String body = mockMvc.perform(get("/api/v1/farms/diag-farm/diagnoses?size=1"))
				.andReturn().getResponse().getContentAsString();
		JsonNode list = jsonMapper.readTree(body).get("diagnoses");
		return list.isEmpty() ? -1 : list.get(0).get("diagnosisId").asLong();
	}

	@Test
	void 토큰이_없으면_사진을_받지_않는다() throws Exception {
		mockMvc.perform(post("/api/v1/farms/diag-farm/images")
						.contentType(MediaType.APPLICATION_JSON)
						.content(image("Tomato", JPEG)))
				.andExpect(status().isUnauthorized());
		assertThat(aiCalls.get()).isZero();
	}

	@Test
	void 사진이_아니면_400을_돌려준다() throws Exception {
		mockMvc.perform(post("/api/v1/farms/diag-farm/images")
						.header("Authorization", "Bearer " + token())
						.contentType(MediaType.APPLICATION_JSON)
						.content(image("Tomato", "not an image".getBytes(StandardCharsets.UTF_8))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("unsupported image format"));
		assertThat(aiCalls.get()).isZero();
	}

	@Test
	void 없는_사진_파일은_404() throws Exception {
		mockMvc.perform(get("/api/v1/images/999999999/file")).andExpect(status().isNotFound());
	}
}
