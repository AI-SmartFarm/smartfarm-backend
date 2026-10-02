package com.smartfarm.backend.diagnosis;

import java.time.LocalDateTime;

import com.smartfarm.backend.ai.DiagnosisResult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * ERD `diagnosis`. 사진 1장당 1건이다. AI 호출이 실패해도 행을 남기고 diagnosed_at을 비워 둔다(ERD 4-6).
 * disease_code는 ERD상 disease_response를 가리키지만 그 테이블이 아직 없어 외래 키는 걸지 않는다.
 * boxes·raw_result는 JSON 문자열을 그대로 넣는다(Hibernate의 JSON 매핑이 Jackson 3을 아직 쓰지 않아서).
 */
@Entity
@Table(name = "diagnosis",
		uniqueConstraints = @UniqueConstraint(name = "uq_diagnosis_image", columnNames = "imageId"),
		indexes = @Index(name = "idx_diagnosis_farm_time", columnList = "farmId, diagnosedAt"))
public class Diagnosis {

	static final int MODEL_VERSION_LENGTH = 30;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long diagnosisId;

	@Column(nullable = false)
	private Long imageId;

	@Column(nullable = false, length = 50)
	private String farmId;

	@Column(length = 30)
	private String diseaseCode;

	@Column(nullable = false)
	private boolean infected;

	@Column(length = 50)
	private String diseaseName;

	private Double confidence;

	@Column(length = 10)
	private String severityLevel;

	private Integer severityRiskCode;

	private Boolean severityLowConfidence;

	@Column(columnDefinition = "json")
	private String boxes;

	@Column(length = MODEL_VERSION_LENGTH)
	private String modelVersion;

	@Column(columnDefinition = "json")
	private String rawResult;

	@Column(nullable = false)
	private LocalDateTime requestedAt;

	private LocalDateTime diagnosedAt;

	protected Diagnosis() {
	}

	static Diagnosis succeeded(long imageId, String farmId, DiagnosisResult result, String boxesJson,
			String modelVersion, String rawResultJson, LocalDateTime requestedAt, LocalDateTime diagnosedAt) {
		Diagnosis d = new Diagnosis();
		d.imageId = imageId;
		d.farmId = farmId;
		d.infected = result.infected();
		d.diseaseCode = result.diseaseCode();
		d.diseaseName = result.diseaseName();
		d.confidence = result.confidence();
		d.severityLevel = result.severityLevel();
		d.severityRiskCode = result.severityRiskCode();
		d.severityLowConfidence = result.infected() ? result.severityLowConfidence() : null;
		d.boxes = boxesJson;
		d.modelVersion = truncate(modelVersion);
		d.rawResult = rawResultJson;
		d.requestedAt = requestedAt;
		d.diagnosedAt = diagnosedAt;
		return d;
	}

	/** AI 호출이나 응답 변환이 실패한 경우. 응답을 받았으면 원인을 볼 수 있게 원본은 남긴다. */
	static Diagnosis failed(long imageId, String farmId, String rawResultJson, LocalDateTime requestedAt) {
		Diagnosis d = new Diagnosis();
		d.imageId = imageId;
		d.farmId = farmId;
		d.infected = false;
		d.rawResult = rawResultJson;
		d.requestedAt = requestedAt;
		return d;
	}

	private static String truncate(String value) {
		return value == null || value.length() <= MODEL_VERSION_LENGTH ? value : value.substring(0, MODEL_VERSION_LENGTH);
	}

	public Long getDiagnosisId() {
		return diagnosisId;
	}

	public Long getImageId() {
		return imageId;
	}

	public String getFarmId() {
		return farmId;
	}

	public boolean isInfected() {
		return infected;
	}

	public String getDiseaseCode() {
		return diseaseCode;
	}

	public String getDiseaseName() {
		return diseaseName;
	}

	public Double getConfidence() {
		return confidence;
	}

	public String getSeverityLevel() {
		return severityLevel;
	}

	public Integer getSeverityRiskCode() {
		return severityRiskCode;
	}

	public Boolean getSeverityLowConfidence() {
		return severityLowConfidence;
	}

	public String getBoxes() {
		return boxes;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public LocalDateTime getDiagnosedAt() {
		return diagnosedAt;
	}
}
