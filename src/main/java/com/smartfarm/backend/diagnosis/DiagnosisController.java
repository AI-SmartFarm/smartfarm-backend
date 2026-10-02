package com.smartfarm.backend.diagnosis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartfarm.backend.common.BadRequestException;
import com.smartfarm.backend.common.ClockConfig;

/** API-008 병해충 진단 이력 조회. 모바일용이라 인증하지 않는다. */
@RestController
public class DiagnosisController {

	private static final int MAX_SIZE = 100;

	/**
	 * API 명세 API-008의 diagnoses[] 항목.
	 * responses(자동 대응 명령)는 제어 명령(API-003)이 구현되기 전이라 항상 빈 배열이고,
	 * guide는 안내 문구를 AI 응답과 disease_response 중 어디서 가져올지 정해지지 않아(ERD 9-4) 아직 null이다.
	 */
	public record Item(
			long diagnosisId,
			OffsetDateTime diagnosedAt,
			boolean infected,
			String diseaseCode,
			String diseaseName,
			String severityLevel,
			Boolean severityLowConfidence,
			Double confidence,
			String imageUrl,
			List<Object> responses,
			String guide) {

		static Item from(Diagnosis d) {
			return new Item(d.getDiagnosisId(), d.getDiagnosedAt().atZone(ClockConfig.ZONE).toOffsetDateTime(),
					d.isInfected(), d.getDiseaseCode(), d.getDiseaseName(), d.getSeverityLevel(),
					d.getSeverityLowConfidence(), d.getConfidence(), "/api/v1/images/" + d.getImageId() + "/file",
					List.of(), null);
		}
	}

	private final DiagnosisService diagnosisService;

	public DiagnosisController(DiagnosisService diagnosisService) {
		this.diagnosisService = diagnosisService;
	}

	@GetMapping("/api/v1/farms/{farmId}/diagnoses")
	public Map<String, List<Item>> history(@PathVariable String farmId,
			@RequestParam(defaultValue = "20") int size) {
		if (size < 1 || size > MAX_SIZE) {
			throw new BadRequestException("size must be 1-" + MAX_SIZE);
		}
		return Map.of("diagnoses", diagnosisService.history(farmId, size).stream().map(Item::from).toList());
	}
}
