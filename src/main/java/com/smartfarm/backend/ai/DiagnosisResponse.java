package com.smartfarm.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** smartfarm-ai의 DiagnosisPipeline.diagnose() JSON. result는 detected / no_detection. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosisResponse(
		String result,
		String crop,
		String message,
		String disclaimer,
		List<Detection> detections,
		@JsonProperty("model_version") String modelVersion) {

	public DiagnosisResponse {
		detections = detections == null ? List.of() : detections;
	}

	public boolean isDetected() {
		return "detected".equals(result);
	}
}
