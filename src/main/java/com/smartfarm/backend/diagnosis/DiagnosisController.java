package com.smartfarm.backend.diagnosis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartfarm.backend.command.CommandService;
import com.smartfarm.backend.command.ControlCommand;
import com.smartfarm.backend.common.BadRequestException;
import com.smartfarm.backend.common.ClockConfig;
import com.smartfarm.backend.control.DiseaseResponse;
import com.smartfarm.backend.control.PestResponseService;
import com.smartfarm.backend.image.ImageService;

/** API-008 병해충 진단 이력 조회. 모바일용이라 인증하지 않는다. */
@RestController
public class DiagnosisController {

	private static final int MAX_SIZE = 100;

	/** 진단 때문에 자동으로 실행된 제어 명령 (API 명세 API-008의 responses[]). */
	public record Response(String commandId, String actuator, String action, Integer durationMinutes, String result) {

		static Response from(ControlCommand c) {
			Integer minutes = c.getDurationSec() == null ? null : c.getDurationSec() / 60;
			return new Response(c.getCommandId(), c.getActuator(), c.getAction(), minutes, c.getStatus());
		}
	}

	/**
	 * API 명세 API-008의 diagnoses[] 항목.
	 * imageUrl은 사진 파일이 남아 있을 때만 준다. 정상 사진은 카메라별 최신 1장만 파일을 남기기 때문이다.
	 * guide는 백엔드의 병해 대응 규칙(disease_response)에서 가져온다. 규칙이 없는 병해는 null이다.
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
			List<Response> responses,
			String guide) {
	}

	private final DiagnosisService diagnosisService;
	private final CommandService commandService;
	private final PestResponseService pestResponseService;
	private final ImageService imageService;

	public DiagnosisController(DiagnosisService diagnosisService, CommandService commandService,
			PestResponseService pestResponseService, ImageService imageService) {
		this.diagnosisService = diagnosisService;
		this.commandService = commandService;
		this.pestResponseService = pestResponseService;
		this.imageService = imageService;
	}

	@GetMapping("/api/v1/farms/{farmId}/diagnoses")
	public Map<String, List<Item>> history(@PathVariable String farmId,
			@RequestParam(defaultValue = "20") int size) {
		if (size < 1 || size > MAX_SIZE) {
			throw new BadRequestException("size must be 1-" + MAX_SIZE);
		}
		List<Diagnosis> diagnoses = diagnosisService.history(farmId, size);
		// 진단마다 조회하지 않고 한 번에 가져온다.
		Map<Long, List<Response>> responses = commandService
				.findByDiagnoses(diagnoses.stream().map(Diagnosis::getDiagnosisId).toList())
				.stream()
				.collect(Collectors.groupingBy(c -> c.getDiagnosisId(),
						Collectors.mapping(Response::from, Collectors.toList())));
		return Map.of("diagnoses", diagnoses.stream()
				.map(d -> toItem(d, responses.getOrDefault(d.getDiagnosisId(), List.of())))
				.toList());
	}

	private Item toItem(Diagnosis d, List<Response> responses) {
		String imageUrl = imageService.find(d.getImageId())
				.filter(imageService::hasFile)
				.map(image -> "/api/v1/images/" + image.getImageId() + "/file")
				.orElse(null);
		String guide = d.isInfected()
				? pestResponseService.guideRule(d.getDiseaseCode()).map(DiseaseResponse::getGuide).orElse(null)
				: null;
		return new Item(d.getDiagnosisId(), d.getDiagnosedAt().atZone(ClockConfig.ZONE).toOffsetDateTime(),
				d.isInfected(), d.getDiseaseCode(), d.getDiseaseName(), d.getSeverityLevel(), d.getSeverityLowConfidence(),
				d.getConfidence(), imageUrl, responses, guide);
	}
}
