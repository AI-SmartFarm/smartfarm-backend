package com.smartfarm.backend.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.smartfarm.backend.common.BadRequestException;
import com.smartfarm.backend.common.ClockConfig;

/**
 * API-001 수신. 최신 상태는 받을 때마다 메모리에 갱신하고, DB 이력은 설정한 간격마다 한 건만 저장한다.
 * 실시간성은 수집 주기가 정하므로 저장 간격을 늘려도 자동제어·현재 상태 반영은 늦어지지 않는다.
 */
@Service
public class TelemetryService {

	private final TelemetryRepository telemetryRepository;
	private final Duration historyInterval;
	private final Clock clock;

	private final Map<String, TelemetrySnapshot> latestByFarm = new ConcurrentHashMap<>();
	private final Map<String, LocalDateTime> lastSavedByFarm = new ConcurrentHashMap<>();

	public TelemetryService(TelemetryRepository telemetryRepository, TelemetryProperties properties, Clock clock) {
		this.telemetryRepository = telemetryRepository;
		this.historyInterval = Duration.ofSeconds(properties.historyIntervalSeconds());
		this.clock = clock;
	}

	/** 저장했으면 true. */
	public boolean receive(String pathFarmId, TelemetryRequest request) {
		if (!pathFarmId.equals(request.farmId())) {
			throw new BadRequestException("farmId mismatch");
		}
		TelemetrySnapshot snapshot = toSnapshot(request);
		latestByFarm.put(snapshot.farmId(), snapshot);

		if (!claimHistorySlot(snapshot.farmId(), snapshot.measuredAt())) {
			return false;
		}
		telemetryRepository.save(new Telemetry(snapshot));
		return true;
	}

	public Optional<TelemetrySnapshot> latest(String farmId) {
		return Optional.ofNullable(latestByFarm.get(farmId));
	}

	/** 동시에 들어온 요청이 같은 간격에 두 번 저장하지 않도록 판단과 기록을 한 번에 한다. */
	private boolean claimHistorySlot(String farmId, LocalDateTime measuredAt) {
		AtomicBoolean claimed = new AtomicBoolean(false);
		lastSavedByFarm.compute(farmId, (id, lastSaved) -> {
			if (lastSaved == null || !measuredAt.isBefore(lastSaved.plus(historyInterval))) {
				claimed.set(true);
				return measuredAt;
			}
			return lastSaved;
		});
		return claimed.get();
	}

	private TelemetrySnapshot toSnapshot(TelemetryRequest request) {
		TelemetryRequest.Sensors s = request.sensors();
		TelemetryRequest.Actuators a = request.actuators();
		TelemetryRequest.Crop crop = request.crop();
		return new TelemetrySnapshot(
				request.farmId(),
				toKst(request.timestampUtc()),
				simTime(request.simTimeUtc()),
				measured(s.tempAvailable(), s.airTempC()),
				measured(s.humidityAvailable(), s.airHumidityPct()),
				measured(s.soilAvailable(), s.soilMoisturePct()),
				measured(s.co2Available(), s.co2Ppm()),
				measured(s.lightAvailable(), s.lightLux()),
				crop == null ? null : blankToNull(crop.species()),
				crop == null ? null : blankToNull(crop.stage()),
				a != null && Boolean.TRUE.equals(a.circFan()),
				a != null && Boolean.TRUE.equals(a.waterPump()),
				a != null && Boolean.TRUE.equals(a.ventFan()),
				LocalDateTime.now(clock));
	}

	/** 설치 여부가 true일 때만 값을 믿는다. 기온은 음수가 될 수 있어 -1만으로는 미설치를 판단하지 않는다. */
	private static Double measured(Boolean available, Double value) {
		return Boolean.TRUE.equals(available) ? value : null;
	}

	private static LocalDateTime toKst(String utc) {
		try {
			return LocalDateTime.ofInstant(Instant.parse(utc), ClockConfig.ZONE);
		}
		catch (DateTimeParseException e) {
			throw new BadRequestException("invalid timestampUtc");
		}
	}

	/** API-001 명세상 simTimeUtc는 이름과 달리 이미 지역 시각이라 시간대 변환 없이 그대로 읽는다. */
	private static LocalDateTime simTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
		}
		catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() || "none".equalsIgnoreCase(value) ? null : value;
	}
}
