package com.smartfarm.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 지식베이스에서 붙는 병 정보. 정상 클래스에는 없다.
 * 근거를 못 찾은 항목은 배열이 비어 있을 수 있다(예: 고추점무늬병의 preventionPrinciples).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Diagnosis(
		@JsonProperty("name_kr") String nameKr,
		@JsonProperty("name_en") String nameEn,
		Cause cause,
		List<String> symptoms,
		@JsonProperty("prevention_principles") List<String> preventionPrinciples,
		List<String> sources,
		@JsonProperty("evidence_level") String evidenceLevel,
		String note) {
}
