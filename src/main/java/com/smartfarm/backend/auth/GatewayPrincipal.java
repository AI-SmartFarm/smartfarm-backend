package com.smartfarm.backend.auth;

/** 검증된 토큰의 주인. 필터가 요청 속성에 넣고, 컨트롤러는 필요할 때 꺼내 쓴다. */
public record GatewayPrincipal(String gatewayId, String farmId) {

	public static final String REQUEST_ATTRIBUTE = GatewayPrincipal.class.getName();
}
