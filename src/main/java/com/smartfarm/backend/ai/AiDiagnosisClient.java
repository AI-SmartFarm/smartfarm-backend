package com.smartfarm.backend.ai;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** smartfarm-ai(FastAPI)의 API-005 `POST /diagnose`를 동기 호출한다. 응답에 API-006(중증도)·API-007(진단 결과)이 함께 담긴다. */
@Component
public class AiDiagnosisClient {

	private static final double DEFAULT_THRESHOLD = 0.15;
	private static final int DEFAULT_TILES = 1;

	private final RestClient aiRestClient;

	public AiDiagnosisClient(RestClient aiRestClient) {
		this.aiRestClient = aiRestClient;
	}

	/** AI 서버의 GET /ping(API 키 검사 포함)을 호출해 연결·인증 상태를 돌려준다. 예외를 던지지 않는다. */
	public AiStatus ping() {
		long start = System.nanoTime();
		try {
			aiRestClient.get().uri("/ping").retrieve().toBodilessEntity();
			return new AiStatus(AiStatus.UP, elapsedMs(start));
		}
		catch (HttpClientErrorException.Unauthorized e) {
			return new AiStatus(AiStatus.UNAUTHORIZED, elapsedMs(start));
		}
		catch (ResourceAccessException e) {
			return new AiStatus(AiStatus.UNREACHABLE, elapsedMs(start));
		}
		catch (RestClientException e) {
			return new AiStatus(AiStatus.ERROR, elapsedMs(start));
		}
	}

	private static long elapsedMs(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	public DiagnosisResponse diagnose(byte[] image, String filename, String crop) {
		return diagnose(image, filename, crop, DEFAULT_THRESHOLD, DEFAULT_TILES);
	}

	/** {@code crop}은 API-004 species를 소문자로 바꾼 값(tomato, pepper, ...). groundTruth* 필드는 절대 보내지 않는다. */
	public DiagnosisResponse diagnose(byte[] image, String filename, String crop, double threshold, int tiles) {
		// MultipartBodyBuilder는 reactive-streams가 클래스패스에 있어야 해서 쓰지 않는다.
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("image", new ByteArrayResource(image) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		body.add("crop", crop);
		body.add("threshold", String.valueOf(threshold));
		body.add("tiles", String.valueOf(tiles));

		DiagnosisResponse response = aiRestClient.post()
				.uri("/diagnose")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body)
				.retrieve()
				.body(DiagnosisResponse.class);
		if (response == null) {
			throw new IllegalStateException("smartfarm-ai returned an empty response");
		}
		return response;
	}
}
