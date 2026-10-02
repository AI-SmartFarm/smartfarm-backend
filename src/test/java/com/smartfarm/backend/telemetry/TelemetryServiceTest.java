package com.smartfarm.backend.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.smartfarm.backend.common.BadRequestException;

class TelemetryServiceTest {

	private final List<Telemetry> saved = new ArrayList<>();
	private TelemetryService service;

	@BeforeEach
	void setUp() {
		// save만 쓰므로 DB 없이 저장된 엔티티를 모으는 가짜 저장소를 쓴다.
		TelemetryRepository repository = (TelemetryRepository) Proxy.newProxyInstance(
				TelemetryRepository.class.getClassLoader(), new Class<?>[] { TelemetryRepository.class },
				(proxy, method, args) -> {
					if (method.getName().equals("save")) {
						saved.add((Telemetry) args[0]);
						return args[0];
					}
					throw new UnsupportedOperationException(method.getName());
				});
		service = new TelemetryService(repository, new TelemetryProperties(60),
				Clock.fixed(Instant.parse("2026-10-01T05:30:05Z"), ZoneOffset.UTC));
	}

	private static TelemetryRequest request(String timestampUtc, boolean soilAvailable) {
		return new TelemetryRequest(
				"greenhouse-01",
				timestampUtc,
				"2025-01-01T09:16:00.000Z",
				new TelemetryRequest.Sensors(21.4, 63.0, soilAvailable ? 55.0 : -1.0, 540.0, 18000.0,
						true, true, soilAvailable, true, true),
				new TelemetryRequest.Actuators(true, false, true),
				new TelemetryRequest.Crop("Tomato", "Vegetative"));
	}

	@Test
	void 받은_값을_한국_시간과_최신_상태로_바꾼다() {
		service.receive("greenhouse-01", request("2026-10-01T05:30:00.000Z", true));

		TelemetrySnapshot latest = service.latest("greenhouse-01").orElseThrow();
		assertThat(latest.measuredAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 14, 30));
		assertThat(latest.simTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 9, 16));
		assertThat(latest.airTempC()).isEqualTo(21.4);
		assertThat(latest.soilMoisturePct()).isEqualTo(55.0);
		assertThat(latest.species()).isEqualTo("Tomato");
		assertThat(latest.circFanOn()).isTrue();
		assertThat(latest.ventFanOn()).isFalse();
	}

	@Test
	void 설치되지_않은_센서의_값은_null로_둔다() {
		service.receive("greenhouse-01", request("2026-10-01T05:30:00.000Z", false));

		assertThat(service.latest("greenhouse-01").orElseThrow().soilMoisturePct()).isNull();
	}

	@Test
	void 이력은_간격마다_한_건만_저장하고_최신값은_매번_갱신한다() {
		assertThat(service.receive("greenhouse-01", request("2026-10-01T05:30:00Z", true))).isTrue();
		assertThat(service.receive("greenhouse-01", request("2026-10-01T05:30:05Z", true))).isFalse();
		assertThat(service.receive("greenhouse-01", request("2026-10-01T05:30:55Z", true))).isFalse();
		assertThat(service.receive("greenhouse-01", request("2026-10-01T05:31:00Z", true))).isTrue();

		assertThat(saved).hasSize(2);
		assertThat(service.latest("greenhouse-01").orElseThrow().measuredAt())
				.isEqualTo(LocalDateTime.of(2026, 10, 1, 14, 31));
	}

	@Test
	void URL과_본문의_농장이_다르면_거절한다() {
		assertThatThrownBy(() -> service.receive("other-farm", request("2026-10-01T05:30:00Z", true)))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("farmId mismatch");
	}

	@Test
	void 시각_형식이_틀리면_거절한다() {
		assertThatThrownBy(() -> service.receive("greenhouse-01", request("yesterday", true)))
				.isInstanceOf(BadRequestException.class);
	}
}
