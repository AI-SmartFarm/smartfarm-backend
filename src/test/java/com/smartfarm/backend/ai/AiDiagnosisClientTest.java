package com.smartfarm.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** 가짜 AI 서버(JDK HttpServer)를 실제로 띄워 multipart 전송과 응답 파싱을 확인한다. */
class AiDiagnosisClientTest {

	private static final String DETECTED = """
			{"result": "detected", "crop": "tomato", "disclaimer": "면책",
			 "detections": [{"class": "tomato_disease18", "confidence": 0.93, "bbox": [1.0, 2.0, 3.0, 4.0],
			   "severity": {"level": "중기", "risk_code": 2, "confidence": 0.81,
			                "model_valid_accuracy": 0.6266, "low_confidence": false},
			   "diagnosis": {"name_kr": "토마토잎곰팡이병", "name_en": "Tomato Leaf Mold",
			                 "cause": {"type": "곰팡이", "pathogen": "Cladosporium fulvum"},
			                 "symptoms": ["s1", "s2"], "prevention_principles": ["p1"], "sources": ["u"],
			                 "unknown_field": 1}}]}
			""";

	private HttpServer server;
	private final AtomicReference<String> path = new AtomicReference<>();
	private final AtomicReference<String> contentType = new AtomicReference<>();
	private final AtomicReference<String> body = new AtomicReference<>();
	private volatile String responseJson = DETECTED;

	@BeforeEach
	void startFakeAiServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
			contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
			byte[] out = responseJson.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, out.length);
			exchange.getResponseBody().write(out);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopFakeAiServer() {
		server.stop(0);
	}

	private AiDiagnosisClient client() {
		return new AiDiagnosisClient(RestClient.builder()
				.baseUrl("http://localhost:" + server.getAddress().getPort())
				.build());
	}

	@Test
	void sendsMultipartAndParsesDetectionWithSeverity() {
		DiagnosisResponse response = client().diagnose(new byte[] {1, 2, 3}, "leaf.jpg", "tomato");

		assertThat(path.get()).isEqualTo("POST /diagnose");
		assertThat(contentType.get()).startsWith("multipart/form-data").contains("boundary=");
		assertThat(body.get()).contains("name=\"image\"; filename=\"leaf.jpg\"");
		assertThat(body.get()).contains("name=\"crop\"").contains("tomato");
		assertThat(body.get()).contains("name=\"threshold\"").contains("0.15");
		assertThat(body.get()).contains("name=\"tiles\"");

		assertThat(response.isDetected()).isTrue();
		Detection detection = response.detections().get(0);
		assertThat(detection.className()).isEqualTo("tomato_disease18");
		assertThat(detection.bbox()).containsExactly(1.0, 2.0, 3.0, 4.0);
		assertThat(detection.severity().level()).isEqualTo("중기");
		assertThat(detection.severity().riskCode()).isEqualTo(2);
		assertThat(detection.severity().lowConfidence()).isFalse();
		assertThat(detection.diagnosis().nameKr()).isEqualTo("토마토잎곰팡이병");
		assertThat(detection.diagnosis().cause().pathogen()).isEqualTo("Cladosporium fulvum");
		assertThat(detection.diagnosis().cause().vector()).isNull();
		assertThat(detection.diagnosis().symptoms()).containsExactly("s1", "s2");
		assertThat(detection.diagnosis().preventionPrinciples()).containsExactly("p1");
	}

	@Test
	void parsesNoDetection() {
		responseJson = """
				{"result": "no_detection", "crop": "tomato", "message": "병징을 찾지 못했습니다"}
				""";

		DiagnosisResponse response = client().diagnose(new byte[] {1}, "leaf.jpg", "tomato");

		assertThat(response.isDetected()).isFalse();
		assertThat(response.detections()).isEmpty();
		assertThat(response.message()).contains("찾지 못했습니다");
	}
}
