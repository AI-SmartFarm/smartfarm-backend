package com.smartfarm.backend.farm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 관리자 화면 없이 개발용 농장과 기기 1대를 등록하기 위한 값. gatewaySecret이 비어 있으면 기기는 등록하지 않는다. */
@ConfigurationProperties(prefix = "seed")
public record SeedProperties(
		@DefaultValue("greenhouse-01") String farmId,
		@DefaultValue("스마트팜 온실 1") String farmName,
		@DefaultValue("SIM001") String gatewayId,
		@DefaultValue("스마트팜 시뮬레이터 1") String gatewayName,
		String gatewaySecret) {
}
