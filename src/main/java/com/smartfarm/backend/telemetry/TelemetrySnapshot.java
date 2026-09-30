package com.smartfarm.backend.telemetry;

import java.time.LocalDateTime;

/**
 * 마지막으로 받은 환경 상태. 자동제어 판단과 현재 상태 조회(API-002)는 DB가 아니라 이 값을 쓴다.
 * 센서가 없으면 해당 값은 null이다.
 */
public record TelemetrySnapshot(
		String farmId,
		LocalDateTime measuredAt,
		LocalDateTime simTime,
		Double airTempC,
		Double airHumidityPct,
		Double soilMoisturePct,
		Double co2Ppm,
		Double lightLux,
		String species,
		String growthStage,
		boolean circFanOn,
		boolean waterPumpOn,
		boolean ventFanOn,
		LocalDateTime receivedAt) {
}
