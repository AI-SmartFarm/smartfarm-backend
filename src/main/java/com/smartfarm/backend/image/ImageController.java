package com.smartfarm.backend.image;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.smartfarm.backend.common.BadRequestException;
import com.smartfarm.backend.diagnosis.DiagnosisService;

import jakarta.validation.Valid;

/**
 * API-004 작물 이미지 전송(JWT는 GatewayAuthFilter가 먼저 검사)과 API-007-B의 사진 파일 조회.
 * 사진을 저장해 200을 돌려준 뒤 진단은 백그라운드에서 한다. 시뮬레이터는 응답의 file만 로그에 쓴다.
 */
@RestController
public class ImageController {

	private final ImageService imageService;
	private final DiagnosisService diagnosisService;

	public ImageController(ImageService imageService, DiagnosisService diagnosisService) {
		this.imageService = imageService;
		this.diagnosisService = diagnosisService;
	}

	@PostMapping("/api/v1/farms/{farmId}/images")
	public Map<String, Object> receive(@PathVariable String farmId, @Valid @RequestBody ImageRequest request) {
		if (!farmId.equals(request.farmId())) {
			throw new BadRequestException("farmId mismatch");
		}
		if (!imageService.shouldStore(farmId, request)) {
			// 저장하지 않은 주기 사진도 시뮬레이터에는 정상 응답한다. 시뮬레이터는 file을 로그에만 쓴다.
			Map<String, Object> skipped = new LinkedHashMap<>();
			skipped.put("ok", true);
			skipped.put("file", null);
			return skipped;
		}
		// receive()의 트랜잭션이 끝난 뒤 진단을 요청해야 진단 스레드가 커밋 전의 사진을 보지 않는다.
		ImageService.Received received = imageService.receive(farmId, request);
		diagnosisService.request(received);
		String filePath = received.image().getFilePath();
		return Map.of("ok", true, "file", filePath.substring(filePath.lastIndexOf('/') + 1));
	}

	@GetMapping("/api/v1/images/{imageId}/file")
	public ResponseEntity<Resource> file(@PathVariable long imageId) {
		return imageService.find(imageId)
				.flatMap(image -> imageService.file(image).map(path -> serve(image, path)))
				.orElse(ResponseEntity.notFound().build());
	}

	private static ResponseEntity<Resource> serve(CropImage image, Path path) {
		MediaType type = "png".equals(image.getImageFormat()) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
		return ResponseEntity.ok().contentType(type).body(new FileSystemResource(path));
	}
}
