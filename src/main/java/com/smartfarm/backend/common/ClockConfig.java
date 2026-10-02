package com.smartfarm.backend.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시각 계산을 테스트에서 고정할 수 있게 Clock을 빈으로 둔다. DB에는 한국 시간으로 저장한다(ERD 작성 규칙). */
@Configuration
public class ClockConfig {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	Clock clock() {
		return Clock.system(ZONE);
	}
}
