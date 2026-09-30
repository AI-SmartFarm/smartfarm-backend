package com.smartfarm.backend.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** AI 서버 연결 상태. UP이 아니면 원인은 status로 구분한다. */
public record AiStatus(String status, long latencyMs) {

	public static final String UP = "UP";
	/** AI 서버가 API 키를 거절했다 (AI_SERVICE_API_KEY가 서버의 API_KEY와 다름). */
	public static final String UNAUTHORIZED = "UNAUTHORIZED";
	/** 연결하지 못했다 (AI 서버가 꺼졌거나, 주소가 틀렸거나, 터널이 바뀜). */
	public static final String UNREACHABLE = "UNREACHABLE";
	/** 연결은 됐지만 예상 밖의 응답이다. */
	public static final String ERROR = "ERROR";

	@JsonIgnore
	public boolean isUp() {
		return UP.equals(status);
	}
}
