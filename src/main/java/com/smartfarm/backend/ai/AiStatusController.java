package com.smartfarm.backend.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포된 백엔드가 AI 서버에 닿는지 curl 한 번으로 확인하는 용도. UP이면 200, 아니면 503. */
@RestController
@RequestMapping("/api/v1/ai")
public class AiStatusController {

	private final AiDiagnosisClient aiDiagnosisClient;

	public AiStatusController(AiDiagnosisClient aiDiagnosisClient) {
		this.aiDiagnosisClient = aiDiagnosisClient;
	}

	@GetMapping("/status")
	public ResponseEntity<AiStatus> status() {
		AiStatus status = aiDiagnosisClient.ping();
		return ResponseEntity.status(status.isUp() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(status);
	}
}
