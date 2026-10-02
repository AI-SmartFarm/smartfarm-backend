package com.smartfarm.backend.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 텔레메트리는 5초마다 오지만 DB에는 이 간격마다 한 건만 남긴다. 간격은 9/29 회의 결과에 따라 바꾼다. */
@ConfigurationProperties(prefix = "telemetry")
public record TelemetryProperties(@DefaultValue("60") long historyIntervalSeconds) {
}
