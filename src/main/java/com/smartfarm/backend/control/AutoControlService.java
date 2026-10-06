package com.smartfarm.backend.control;

import org.springframework.stereotype.Service;

import com.smartfarm.backend.command.CommandService;
import com.smartfarm.backend.command.ControlCommand;
import com.smartfarm.backend.control.Hysteresis.Decision;
import com.smartfarm.backend.crop.CropProfile;
import com.smartfarm.backend.crop.CropProfileRepository;
import com.smartfarm.backend.telemetry.TelemetrySnapshot;

/**
 * 환경 자동제어(기능 명세 F-02). 텔레메트리를 받을 때마다 작물 기준값과 비교해 순환팬·관수 명령을 만든다.
 * 장치의 현재 상태는 시뮬레이터가 보고한 값을 쓰므로, 명령이 만료되거나 거절돼도 다음 텔레메트리에서 다시 판단한다.
 */
@Service
public class AutoControlService {

	static final String CIRC_FAN = "circFan";
	static final String WATER_PUMP = "waterPump";

	private final CropProfileRepository cropProfileRepository;
	private final CommandService commandService;

	public AutoControlService(CropProfileRepository cropProfileRepository, CommandService commandService) {
		this.cropProfileRepository = cropProfileRepository;
		this.commandService = commandService;
	}

	public void evaluate(TelemetrySnapshot snapshot) {
		if (snapshot.species() == null) {
			return;
		}
		// 기준값이 없는 작물은 잘못된 기준으로 제어하지 않도록 건너뛴다.
		CropProfile profile = cropProfileRepository.findById(snapshot.species()).orElse(null);
		if (profile == null) {
			return;
		}

		if (snapshot.airTempC() != null) {
			Decision fan = Hysteresis.whenHigh(snapshot.airTempC(), profile.getFanOnTempC(), profile.getFanOffTempC(),
					snapshot.circFanOn());
			issue(snapshot.farmId(), CIRC_FAN, fan, "TEMP_HIGH", "TEMP_NORMAL", snapshot.airTempC());
		}
		if (snapshot.soilMoisturePct() != null) {
			Decision pump = Hysteresis.whenLow(snapshot.soilMoisturePct(), profile.getIrrigationOnPct(),
					profile.getIrrigationOffPct(), snapshot.waterPumpOn());
			issue(snapshot.farmId(), WATER_PUMP, pump, "SOIL_LOW", "SOIL_ENOUGH", snapshot.soilMoisturePct());
		}
	}

	private void issue(String farmId, String actuator, Decision decision, String onReason, String offReason,
			double value) {
		if (decision == Decision.KEEP) {
			return;
		}
		boolean on = decision == Decision.TURN_ON;
		commandService.enqueueIfIdle(farmId, actuator, on ? "on" : "off", ControlCommand.SOURCE_AUTO,
				on ? onReason : offReason, value);
	}
}
