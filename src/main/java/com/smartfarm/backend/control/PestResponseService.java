package com.smartfarm.backend.control;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.smartfarm.backend.command.CommandService;

/**
 * AI 진단 기반 자동 대응(기능 명세 F-04). AI는 진단 결과만 주고, 대응 여부와 방식은 백엔드가 병해별 규칙으로 정한다.
 * 규칙이 없는 병해는 잘못된 대응을 하지 않도록 아무것도 하지 않는다.
 */
@Service
public class PestResponseService {

	private static final Logger log = LoggerFactory.getLogger(PestResponseService.class);

	private final DiseaseResponseRepository ruleRepository;
	private final CommandService commandService;

	public PestResponseService(DiseaseResponseRepository ruleRepository, CommandService commandService) {
		this.ruleRepository = ruleRepository;
		this.commandService = commandService;
	}

	/** 감염으로 판정된 진단에 대해 호출한다. 대응 명령을 만들었으면 true. */
	public boolean respond(String farmId, long diagnosisId, String diseaseCode) {
		Optional<DiseaseResponse> rule = guideRule(diseaseCode);
		if (rule.isEmpty() || !rule.get().turnsFanOn()) {
			return false;
		}
		boolean created = commandService.enqueuePestResponse(farmId, AutoControlService.CIRC_FAN, diagnosisId,
				rule.get().getDurationMin() * 60);
		log.info("병해 자동 대응 {}: 진단 {} ({}) → 순환팬 {}분", created ? "명령 생성" : "이미 유지 중", diagnosisId, diseaseCode,
				rule.get().getDurationMin());
		return created;
	}

	public Optional<DiseaseResponse> guideRule(String diseaseCode) {
		return diseaseCode == null ? Optional.empty() : ruleRepository.findById(diseaseCode);
	}
}
