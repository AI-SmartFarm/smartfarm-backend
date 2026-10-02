package com.smartfarm.backend.telemetry;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * ERD `telemetry`. 그래프·이력 조회용으로 주기마다 한 건만 저장한다.
 * 원본 JSON(raw_payload)은 저장 여부가 정해지지 않아 아직 두지 않는다.
 */
@Entity
@Table(name = "telemetry", indexes = @Index(name = "idx_telemetry_farm_time", columnList = "farmId, measuredAt"))
public class Telemetry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long telemetryId;

	@Column(nullable = false, length = 50)
	private String farmId;

	@Column(nullable = false)
	private LocalDateTime measuredAt;

	private LocalDateTime simTime;

	// 기본 이름 규칙은 끝 글자 하나짜리 단어(C)를 붙여 air_tempc로 만들어서 ERD 이름을 직접 적는다.
	@Column(name = "air_temp_c")
	private Double airTempC;

	private Double airHumidityPct;

	private Double soilMoisturePct;

	private Double co2Ppm;

	private Double lightLux;

	@Column(length = 20)
	private String species;

	@Column(length = 20)
	private String growthStage;

	@Column(nullable = false)
	private LocalDateTime receivedAt;

	protected Telemetry() {
	}

	public Telemetry(TelemetrySnapshot snapshot) {
		this.farmId = snapshot.farmId();
		this.measuredAt = snapshot.measuredAt();
		this.simTime = snapshot.simTime();
		this.airTempC = snapshot.airTempC();
		this.airHumidityPct = snapshot.airHumidityPct();
		this.soilMoisturePct = snapshot.soilMoisturePct();
		this.co2Ppm = snapshot.co2Ppm();
		this.lightLux = snapshot.lightLux();
		this.species = snapshot.species();
		this.growthStage = snapshot.growthStage();
		this.receivedAt = snapshot.receivedAt();
	}

	public Long getTelemetryId() {
		return telemetryId;
	}

	public String getFarmId() {
		return farmId;
	}

	public LocalDateTime getMeasuredAt() {
		return measuredAt;
	}

	public Double getAirTempC() {
		return airTempC;
	}

	public Double getSoilMoisturePct() {
		return soilMoisturePct;
	}
}
