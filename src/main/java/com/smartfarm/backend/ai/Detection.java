package com.smartfarm.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 탐지 1건. className은 모델 클래스(예: tomato_disease18), bbox는 [x0, y0, x1, y1] 원본 픽셀. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Detection(
		@JsonProperty("class") String className,
		double confidence,
		List<Double> bbox,
		Severity severity,
		Diagnosis diagnosis) {
}
