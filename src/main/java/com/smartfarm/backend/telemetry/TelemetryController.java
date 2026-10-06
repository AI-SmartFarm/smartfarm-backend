package com.smartfarm.backend.telemetry;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.smartfarm.backend.control.AutoControlService;

import jakarta.validation.Valid;

/** API-001 환경 데이터 전송. JWT 검사는 GatewayAuthFilter가 먼저 한다. 시뮬레이터는 응답 본문을 쓰지 않는다. */
@RestController
public class TelemetryController {

	private final TelemetryService telemetryService;
	private final AutoControlService autoControlService;

	public TelemetryController(TelemetryService telemetryService, AutoControlService autoControlService) {
		this.telemetryService = telemetryService;
		this.autoControlService = autoControlService;
	}

	@PostMapping("/api/v1/farms/{farmId}/telemetry")
	public Map<String, Boolean> receive(@PathVariable String farmId, @Valid @RequestBody TelemetryRequest request) {
		telemetryService.receive(farmId, request);
		// 저장 주기와 상관없이 받을 때마다 판단해야 제어가 늦어지지 않는다.
		telemetryService.latest(farmId).ifPresent(autoControlService::evaluate);
		return Map.of("ok", true);
	}
}
