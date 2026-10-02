package com.smartfarm.backend.auth;

/** 토큰이 없거나 서명·만료가 맞지 않을 때. 원인을 구분해 알려주면 공격에 쓰일 수 있어 메시지는 하나로 둔다. */
public class InvalidTokenException extends RuntimeException {

	public InvalidTokenException() {
		super("invalid or expired access token");
	}
}
