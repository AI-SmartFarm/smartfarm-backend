package com.smartfarm.backend.farm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버 시작 시 개발용 농장과 기기가 없으면 등록한다. 이미 있으면 건드리지 않는다.
 * 기기 비밀값은 환경 변수로만 받고, DB에는 BCrypt 해시만 남긴다.
 */
@Component
class SeedDataLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

	private final SeedProperties seed;
	private final FarmRepository farmRepository;
	private final GatewayRepository gatewayRepository;
	private final PasswordEncoder passwordEncoder;

	SeedDataLoader(SeedProperties seed, FarmRepository farmRepository, GatewayRepository gatewayRepository,
			PasswordEncoder passwordEncoder) {
		this.seed = seed;
		this.farmRepository = farmRepository;
		this.gatewayRepository = gatewayRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!farmRepository.existsById(seed.farmId())) {
			farmRepository.save(new Farm(seed.farmId(), seed.farmName()));
			log.info("농장 등록: {}", seed.farmId());
		}

		if (seed.gatewaySecret() == null || seed.gatewaySecret().isBlank()) {
			log.warn("SEED_GATEWAY_SECRET이 없어 기기를 등록하지 않았다. 토큰 발급(API-008)을 쓰려면 설정해야 한다.");
			return;
		}
		if (!gatewayRepository.existsById(seed.gatewayId())) {
			gatewayRepository.save(new Gateway(seed.gatewayId(), seed.farmId(), seed.gatewayName(), "SIMULATOR",
					passwordEncoder.encode(seed.gatewaySecret())));
			log.info("기기 등록: {} (농장 {})", seed.gatewayId(), seed.farmId());
		}
	}
}
