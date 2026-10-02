package com.smartfarm.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제로 떠 있는 AI 서버에 데모 샘플 이미지를 보내, 백엔드의 클라이언트와 변환 코드(DiagnosisResult)가 끝까지 동작하는지 본다.
 * 환경변수가 없으면 건너뛴다(CI에서는 실행되지 않는다).
 * AI_E2E_URL=https://… AI_E2E_KEY=… AI_E2E_SAMPLES=D:/smartfarm-demo-samples ./gradlew test --tests '*AiServerE2ETest'
 */
@EnabledIfEnvironmentVariable(named = "AI_E2E_URL", matches = ".+")
class AiServerE2ETest {

	private static final int PER_CLASS = 3;

	private final AiServiceProperties properties = new AiServiceProperties(
			System.getenv("AI_E2E_URL"), System.getenv("AI_E2E_KEY"), 5000, 60000);
	private final AiDiagnosisClient client = new AiDiagnosisClient(new AiClientConfig().aiRestClient(properties));

	@Test
	void pingsWithKey() {
		assertThat(client.ping().status()).isEqualTo(AiStatus.UP);
	}

	@Test
	void diagnosesDemoSamplesAndConvertsToTeamFormat() throws Exception {
		Path root = Path.of(System.getenv("AI_E2E_SAMPLES"));
		List<String> lines = Files.readAllLines(root.resolve("정답목록.csv"), StandardCharsets.UTF_8);
		Map<String, Integer> taken = new LinkedHashMap<>();
		Map<String, int[]> score = new LinkedHashMap<>(); // [시도, 정답, 중증도 있음]
		List<String> wrong = new ArrayList<>();

		for (String line : lines.subList(1, lines.size())) {
			String[] c = line.replace("\uFEFF", "").split(",");
			String crop = c[0];
			String expected = c[1];
			if (taken.merge(expected, 1, Integer::sum) > PER_CLASS) {
				continue;
			}
			byte[] image = Files.readAllBytes(root.resolve(c[3]));
			DiagnosisResult result = DiagnosisResult.from(client.diagnose(image, "sample.jpg", crop));

			boolean healthy = DiseaseCatalog.isNormal(expected);
			boolean ok = healthy
					? !result.infected()
					: result.infected() && result.diseaseCode().equals(DiseaseCatalog.byClass(expected).label());
			int[] s = score.computeIfAbsent(expected, k -> new int[3]);
			s[0]++;
			s[1] += ok ? 1 : 0;
			if (result.infected()) {
				assertThat(result.diseaseName()).isNotBlank();
				assertThat(result.boxes()).isNotEmpty();
				assertThat(result.severityLevel()).isIn("초기", "중기", "말기");
				s[2]++;
			}
			if (!ok) {
				wrong.add(expected + " ← " + c[3] + " → " + result.diseaseCode());
			}
		}

		int tried = 0;
		int hit = 0;
		for (Map.Entry<String, int[]> e : score.entrySet()) {
			int[] s = e.getValue();
			System.out.printf("[E2E] %-20s 정답 %d/%d, 병으로 판정 %d건%n", e.getKey(), s[1], s[0], s[2]);
			tried += s[0];
			hit += s[1];
		}
		System.out.printf("[E2E] 합계 %d/%d%n", hit, tried);
		wrong.forEach(w -> System.out.println("[E2E] 오답 " + w));

		assertThat(score).hasSize(14);
		assertThat((double) hit / tried).isGreaterThanOrEqualTo(0.8);
	}
}
