package com.smartfarm.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** API-008. 기기용 JWT를 발급하고 검증한다. 토큰에는 기기 ID(sub)와 농장 ID(farmId)만 담는다. */
@Component
public class JwtTokenService {

	private static final String FARM_ID_CLAIM = "farmId";

	private final SecretKey key;
	private final JwtProperties properties;
	private final Clock clock;

	public JwtTokenService(JwtProperties properties, Clock clock) {
		if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 한다");
		}
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.properties = properties;
		this.clock = clock;
	}

	public String issue(String gatewayId, String farmId) {
		Instant now = clock.instant();
		return Jwts.builder()
				.issuer(properties.issuer())
				.subject(gatewayId)
				.claim(FARM_ID_CLAIM, farmId)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(properties.expiresInSeconds())))
				.signWith(key)
				.compact();
	}

	public long expiresInSeconds() {
		return properties.expiresInSeconds();
	}

	/** 서명·만료·발급자가 맞지 않으면 InvalidTokenException을 던진다. */
	public GatewayPrincipal verify(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.requireIssuer(properties.issuer())
					.clock(() -> Date.from(clock.instant()))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			String farmId = claims.get(FARM_ID_CLAIM, String.class);
			if (claims.getSubject() == null || farmId == null) {
				throw new InvalidTokenException();
			}
			return new GatewayPrincipal(claims.getSubject(), farmId);
		}
		catch (JwtException | IllegalArgumentException e) {
			throw new InvalidTokenException();
		}
	}
}
