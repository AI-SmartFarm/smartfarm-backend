package com.smartfarm.backend.control;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ERD `disease_response`. 병해별로 백엔드가 어떤 장치를 얼마나 켤지 정하는 규칙이다.
 * AI는 진단만 하고 장치를 직접 제어하지 않으므로(기능 명세 F-04) 대응 규칙은 백엔드에 둔다.
 */
@Entity
@Table(name = "disease_response")
public class DiseaseResponse {

	public static final String FAN_ON = "FAN_ON";
	public static final String NOTIFY_ONLY = "NOTIFY_ONLY";

	/** AI 결과를 바꾼 팀 병해 코드 (예: tomato-A). */
	@Id
	@Column(length = 30)
	private String diseaseCode;

	@Column(nullable = false, length = 20)
	private String species;

	@Column(nullable = false, length = 50)
	private String diseaseName;

	@Column(nullable = false, length = 20)
	private String responseAction;

	private Integer durationMin;

	@Column(length = 255)
	private String guide;

	protected DiseaseResponse() {
	}

	public DiseaseResponse(String diseaseCode, String species, String diseaseName, String responseAction,
			Integer durationMin, String guide) {
		if (FAN_ON.equals(responseAction) && (durationMin == null || durationMin <= 0)) {
			throw new IllegalArgumentException("FAN_ON에는 가동 시간이 필요하다: " + diseaseCode);
		}
		this.diseaseCode = diseaseCode;
		this.species = species;
		this.diseaseName = diseaseName;
		this.responseAction = responseAction;
		this.durationMin = durationMin;
		this.guide = guide;
	}

	public boolean turnsFanOn() {
		return FAN_ON.equals(responseAction);
	}

	public String getDiseaseCode() {
		return diseaseCode;
	}

	public Integer getDurationMin() {
		return durationMin;
	}

	public String getGuide() {
		return guide;
	}
}
