package com.smartfarm.backend.diagnosis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진을 받으면 AI 진단을 백그라운드에서 한 장씩 처리한다. 시뮬레이터가 AI 응답(최대 30초)을 기다리지 않게 하기 위해서다.
 * 대기열이 차면(AI가 느리거나 꺼짐) 새 사진은 진단하지 않고 건너뛴다. async=false는 테스트에서 바로 확인하기 위한 값이다.
 */
@ConfigurationProperties(prefix = "diagnosis")
public record DiagnosisProperties(
		@DefaultValue("true") boolean async,
		@DefaultValue("50") int queueCapacity) {
}
