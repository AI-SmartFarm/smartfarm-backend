package com.smartfarm.backend.telemetry;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** API-001 환경 데이터 전송. JWT 검사는 GatewayAuthFilter가 먼저 한다. 시뮬레이터는 응답 본문을 쓰지 않는다. */
@RestController
public class TelemetryController {

	private final TelemetryService telemetryService;

	public TelemetryController(TelemetryService telemetryService) {
		this.telemetryService = telemetryService;
	}

	@PostMapping("/api/v1/farms/{farmId}/telemetry")
	public Map<String, Boolean> receive(@PathVariable String farmId, @Valid @RequestBody TelemetryRequest request) {
		telemetryService.receive(farmId, request);
		return Map.of("ok", true);
	}
}
