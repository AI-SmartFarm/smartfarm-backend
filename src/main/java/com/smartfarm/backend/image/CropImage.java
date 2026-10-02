package com.smartfarm.backend.image;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * ERD `crop_image`. 파일은 디스크에 두고 경로와 메타데이터만 저장한다.
 * gt_* 는 시뮬레이터가 보내는 평가용 정답이라 AI 진단 요청에는 절대 넣지 않는다(API-004).
 */
@Entity
@Table(name = "crop_image", indexes = @Index(name = "idx_image_farm_time", columnList = "farmId, capturedAt"))
public class CropImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long imageId;

	@Column(nullable = false, length = 50)
	private String farmId;

	@Column(nullable = false)
	private LocalDateTime capturedAt;

	@Column(nullable = false, length = 10)
	private String triggerType;

	@Column(nullable = false, length = 20)
	private String source;

	@Column(length = 60)
	private String cameraId;

	@Column(length = 20)
	private String species;

	private Integer cellX;

	private Integer cellZ;

	@Column(nullable = false)
	private String filePath;

	@Column(nullable = false, length = 10)
	private String imageFormat;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	@Column(nullable = false)
	private int sizeBytes;

	@Column(length = 20)
	private String gtStage;

	@Column(length = 30)
	private String gtPestLabel;

	private Double gtPestSeverity;

	@Column(nullable = false)
	private LocalDateTime receivedAt;

	protected CropImage() {
	}

	CropImage(String farmId, LocalDateTime capturedAt, ImageRequest request, String species, String filePath,
			String imageFormat, int sizeBytes, LocalDateTime receivedAt) {
		this.farmId = farmId;
		this.capturedAt = capturedAt;
		this.triggerType = request.trigger();
		this.source = request.source();
		this.cameraId = request.cameraId();
		this.species = species;
		this.cellX = noneToNull(request.cellX());
		this.cellZ = noneToNull(request.cellZ());
		this.filePath = filePath;
		this.imageFormat = imageFormat;
		this.width = request.width();
		this.height = request.height();
		this.sizeBytes = sizeBytes;
		this.gtStage = request.groundTruthStage();
		this.gtPestLabel = request.groundTruthPestLabel();
		this.gtPestSeverity = request.groundTruthPestSeverity();
		this.receivedAt = receivedAt;
	}

	/** 식물이 없으면 시뮬레이터가 -1을 보낸다. */
	private static Integer noneToNull(Integer cell) {
		return cell == null || cell < 0 ? null : cell;
	}

	public Long getImageId() {
		return imageId;
	}

	public String getFarmId() {
		return farmId;
	}

	public LocalDateTime getCapturedAt() {
		return capturedAt;
	}

	/** 식물이 찍히지 않았으면 null이다. 이때는 진단하지 않는다. */
	public String getSpecies() {
		return species;
	}

	public String getFilePath() {
		return filePath;
	}

	public String getImageFormat() {
		return imageFormat;
	}

	public String getGtPestLabel() {
		return gtPestLabel;
	}
}
