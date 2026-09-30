package com.smartfarm.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** secret은 HS256 서명 키라 32바이트 이상이어야 한다. 서버에서는 환경 변수 JWT_SECRET으로만 넣는다. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String secret,
		@DefaultValue("3600") long expiresInSeconds,
		@DefaultValue("smartfarm-backend") String issuer) {
}
