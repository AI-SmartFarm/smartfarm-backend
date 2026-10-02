package com.smartfarm.backend.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API-004 요청 본문 중 백엔드가 쓰는 필드만 받는다. 필드 이름은 노현석 님 API-004 명세를 그대로 따른다.
 * format은 받지만 믿지 않고 파일 앞 바이트로 다시 판별한다. estimate·datasetFile은 쓰지 않아 받지 않는다.
 * cameraId는 ERD v0.2에서 추가된 값이라 시뮬레이터가 아직 보내지 않을 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageRequest(
		@NotBlank String farmId,
		@NotBlank String timestampUtc,
		String format,
		@NotNull Integer width,
		@NotNull Integer height,
		@NotBlank String imageBase64,
		@NotBlank String source,
		@NotBlank String trigger,
		String species,
		String cameraId,
		Integer cellX,
		Integer cellZ,
		String groundTruthStage,
		String groundTruthPestLabel,
		Double groundTruthPestSeverity) {
}
