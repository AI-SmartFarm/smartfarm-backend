package com.smartfarm.backend.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API-001 요청 본문 중 백엔드가 쓰는 필드만 받는다. 날씨·경제성·구역 등 나머지는 무시한다.
 * 필드 이름은 노현석 님 API-001 명세를 그대로 따른다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryRequest(
		@NotBlank String farmId,
		@NotBlank String timestampUtc,
		String simTimeUtc,
		@NotNull @Valid Sensors sensors,
		Actuators actuators,
		Crop crop) {

	/**
	 * 센서가 설치되지 않으면 값이 -1로 오고 xxxAvailable이 false다.
	 * Jackson 3은 빠진 필드를 기본 타입에 넣으면 실패하므로, 필드가 빠져도 받을 수 있게 래퍼 타입을 쓴다.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Sensors(
			Double airTempC,
			Double airHumidityPct,
			Double soilMoisturePct,
			Double co2Ppm,
			Double lightLux,
			Boolean tempAvailable,
			Boolean humidityAvailable,
			Boolean soilAvailable,
			Boolean co2Available,
			Boolean lightAvailable) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Actuators(Boolean waterPump, Boolean ventFan, Boolean circFan) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Crop(String species, String stage) {
	}
}
