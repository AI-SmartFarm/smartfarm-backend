package com.smartfarm.backend.common;

/** 400으로 응답할 요청 오류. 메시지는 그대로 응답 본문 error에 들어간다. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
