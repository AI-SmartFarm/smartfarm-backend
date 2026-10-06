package com.smartfarm.backend.crop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준값이 없는 작물은 자동제어를 하지 않으므로, 서버 시작 시 토마토 기준값이 없으면 넣는다.
 * 값은 UI 시안(적정 22~26℃, 30~50%)에서 가져온 임시값이며 ERD 명세서 8장에 근거를 적었다.
 * 나머지 4개 작물은 기준값이 정해지면 DB에 추가한다. 이미 있는 값은 덮어쓰지 않는다.
 */
@Component
class CropProfileSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CropProfileSeeder.class);

	private final CropProfileRepository cropProfileRepository;

	CropProfileSeeder(CropProfileRepository cropProfileRepository) {
		this.cropProfileRepository = cropProfileRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!cropProfileRepository.existsById("Tomato")) {
			cropProfileRepository.save(new CropProfile("Tomato", "토마토", 22, 26, 27, 25, 30, 50, 30, 50));
			log.info("작물 기준값 등록: Tomato (임시값)");
		}
	}
}
