package com.smartfarm.backend.command;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ttlSeconds: 이 시간 안에 결과 보고가 없으면 명령을 만료시킨다. 시뮬레이터가 오래 꺼져 있다 켜졌을 때 묵은 명령이 한꺼번에 실행되지 않게 한다.
 * cooldownSeconds: 같은 장치에 새 명령을 내기 전 최소 간격. 장치 상태가 다음 텔레메트리에 반영되기 전이나 거절된 직후에 같은 명령을 반복하지 않게 한다.
 */
@ConfigurationProperties(prefix = "control.command")
public record CommandProperties(
		@DefaultValue("60") long ttlSeconds,
		@DefaultValue("30") long cooldownSeconds) {
}
