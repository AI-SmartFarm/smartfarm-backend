package com.smartfarm.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** smartfarm-ai의 DiagnosisPipeline.diagnose() JSON. result는 detected / no_detection. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosisResponse(
		String result,
		String crop,
		String message,
		String disclaimer,
		List<Detection> detections) {

	public DiagnosisResponse {
		detections = detections == null ? List.of() : detections;
	}

	public boolean isDetected() {
		return "detected".equals(result);
	}
}
