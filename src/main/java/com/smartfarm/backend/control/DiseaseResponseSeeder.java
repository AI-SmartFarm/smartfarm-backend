package com.smartfarm.backend.control;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 규칙이 없는 병해는 자동 대응하지 않으므로, 근거 자료가 있는 토마토 2종만 서버 시작 시 넣는다(ERD 8장).
 * 잎곰팡이병은 과습이 원인이라 환기가 핵심이고, 60분은 UI 시안의 "60분 연속 가동"이다.
 * 황화잎말이바이러스는 치료 약제가 없어 장치로 할 수 있는 대응이 없다(노션 "토마토 생육 환경").
 * 이미 있는 규칙은 덮어쓰지 않는다.
 */
@Component
class DiseaseResponseSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DiseaseResponseSeeder.class);

	private static final List<DiseaseResponse> DEFAULTS = List.of(
			new DiseaseResponse("tomato-A", "Tomato", "잎곰팡이병", DiseaseResponse.FAN_ON, 60,
					"환기를 철저히 해 과습을 막고, 병든 잎은 일찍 제거하세요."),
			new DiseaseResponse("tomato-B", "Tomato", "황화잎말이바이러스", DiseaseResponse.NOTIFY_ONLY, null,
					"치료 약제가 없습니다. 감염된 포기를 바로 제거하고 담배가루이를 방제하세요."));

	private final DiseaseResponseRepository repository;

	DiseaseResponseSeeder(DiseaseResponseRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (DiseaseResponse rule : DEFAULTS) {
			if (!repository.existsById(rule.getDiseaseCode())) {
				repository.save(rule);
				log.info("병해 대응 규칙 등록: {}", rule.getDiseaseCode());
			}
		}
	}
}
