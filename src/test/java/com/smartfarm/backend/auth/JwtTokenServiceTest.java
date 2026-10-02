package com.smartfarm.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

	// 테스트용 가짜 키. 비밀값 검사에 걸리지 않도록 반복 문자로 만든다.
	private static final String SECRET = "a".repeat(40);
	private static final Instant NOW = Instant.parse("2026-10-01T05:00:00Z");

	private JwtTokenService serviceAt(Instant now, String secret) {
		return new JwtTokenService(new JwtProperties(secret, 3600, "smartfarm-backend"), Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void 발급한_토큰을_검증하면_기기와_농장을_돌려준다() {
		JwtTokenService service = serviceAt(NOW, SECRET);

		GatewayPrincipal principal = service.verify(service.issue("SIM001", "greenhouse-01"));

		assertThat(principal).isEqualTo(new GatewayPrincipal("SIM001", "greenhouse-01"));
	}

	@Test
	void 만료된_토큰은_거절한다() {
		String token = serviceAt(NOW, SECRET).issue("SIM001", "greenhouse-01");

		JwtTokenService oneHourLater = serviceAt(NOW.plusSeconds(3601), SECRET);

		assertThatThrownBy(() -> oneHourLater.verify(token)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 다른_키로_서명한_토큰은_거절한다() {
		String token = serviceAt(NOW, "b".repeat(40)).issue("SIM001", "greenhouse-01");

		assertThatThrownBy(() -> serviceAt(NOW, SECRET).verify(token)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 형식이_깨진_토큰은_거절한다() {
		assertThatThrownBy(() -> serviceAt(NOW, SECRET).verify("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void 서명_키가_32바이트보다_짧으면_시작하지_않는다() {
		assertThatThrownBy(() -> serviceAt(NOW, "short")).isInstanceOf(IllegalStateException.class);
	}
}
