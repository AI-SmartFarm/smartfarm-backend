package com.smartfarm.backend.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** API-006 중증도. lowConfidence가 true면 참고용으로만 보여줘야 한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Severity(
		String level,
		@JsonProperty("risk_code") int riskCode,
		double confidence,
		@JsonProperty("model_valid_accuracy") Double modelValidAccuracy,
		@JsonProperty("low_confidence") boolean lowConfidence) {
}
