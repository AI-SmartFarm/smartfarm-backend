package com.smartfarm.backend.crop;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ERD `crop_profile`. 작물별 적정 범위와 제어 임계값. 기준값을 코드에 두지 않고 여기서 읽는다.
 * 켜는 기준과 끄는 기준을 따로 둬서 기준값 근처에서 장치가 반복해 켜졌다 꺼지는 것을 막는다(히스테리시스).
 */
@Entity
@Table(name = "crop_profile")
public class CropProfile {

	/** API-001 crop.species 값 그대로 (Tomato, Pepper, Cucumber, Strawberry, Lettuce). */
	@Id
	@Column(length = 20)
	private String species;

	@Column(nullable = false, length = 20)
	private String nameKo;

	@Column(name = "target_temp_min_c", nullable = false)
	private double targetTempMinC;

	@Column(name = "target_temp_max_c", nullable = false)
	private double targetTempMaxC;

	@Column(name = "fan_on_temp_c", nullable = false)
	private double fanOnTempC;

	@Column(name = "fan_off_temp_c", nullable = false)
	private double fanOffTempC;

	@Column(nullable = false)
	private double targetSoilMinPct;

	@Column(nullable = false)
	private double targetSoilMaxPct;

	@Column(nullable = false)
	private double irrigationOnPct;

	@Column(nullable = false)
	private double irrigationOffPct;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected CropProfile() {
	}

	public CropProfile(String species, String nameKo, double targetTempMinC, double targetTempMaxC, double fanOnTempC,
			double fanOffTempC, double targetSoilMinPct, double targetSoilMaxPct, double irrigationOnPct,
			double irrigationOffPct) {
		if (fanOffTempC >= fanOnTempC || irrigationOnPct >= irrigationOffPct) {
			throw new IllegalArgumentException("켜는 기준과 끄는 기준의 순서가 잘못됐다: " + species);
		}
		this.species = species;
		this.nameKo = nameKo;
		this.targetTempMinC = targetTempMinC;
		this.targetTempMaxC = targetTempMaxC;
		this.fanOnTempC = fanOnTempC;
		this.fanOffTempC = fanOffTempC;
		this.targetSoilMinPct = targetSoilMinPct;
		this.targetSoilMaxPct = targetSoilMaxPct;
		this.irrigationOnPct = irrigationOnPct;
		this.irrigationOffPct = irrigationOffPct;
		this.updatedAt = LocalDateTime.now();
	}

	public String getSpecies() {
		return species;
	}

	public String getNameKo() {
		return nameKo;
	}

	public double getTargetTempMinC() {
		return targetTempMinC;
	}

	public double getTargetTempMaxC() {
		return targetTempMaxC;
	}

	public double getFanOnTempC() {
		return fanOnTempC;
	}

	public double getFanOffTempC() {
		return fanOffTempC;
	}

	public double getTargetSoilMinPct() {
		return targetSoilMinPct;
	}

	public double getTargetSoilMaxPct() {
		return targetSoilMaxPct;
	}

	public double getIrrigationOnPct() {
		return irrigationOnPct;
	}

	public double getIrrigationOffPct() {
		return irrigationOffPct;
	}
}
