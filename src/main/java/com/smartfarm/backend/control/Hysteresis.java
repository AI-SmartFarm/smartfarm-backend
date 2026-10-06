package com.smartfarm.backend.control;

/**
 * 켜는 기준과 끄는 기준이 다른 제어 판단(기능 명세 F-02).
 * 두 기준 사이에서는 지금 상태를 유지해, 기준값 근처에서 장치가 반복해 켜졌다 꺼지는 것을 막는다.
 */
final class Hysteresis {

	enum Decision {
		TURN_ON, TURN_OFF, KEEP
	}

	private Hysteresis() {
	}

	/** 값이 높을 때 켜는 장치(순환팬): onAt 이상이면 켜고, offAt 이하로 내려오면 끈다. */
	static Decision whenHigh(double value, double onAt, double offAt, boolean currentlyOn) {
		if (value >= onAt) {
			return currentlyOn ? Decision.KEEP : Decision.TURN_ON;
		}
		if (value <= offAt) {
			return currentlyOn ? Decision.TURN_OFF : Decision.KEEP;
		}
		return Decision.KEEP;
	}

	/** 값이 낮을 때 켜는 장치(관수): onAt 이하이면 켜고, offAt 이상으로 올라오면 끈다. */
	static Decision whenLow(double value, double onAt, double offAt, boolean currentlyOn) {
		if (value <= onAt) {
			return currentlyOn ? Decision.KEEP : Decision.TURN_ON;
		}
		if (value >= offAt) {
			return currentlyOn ? Decision.TURN_OFF : Decision.KEEP;
		}
		return Decision.KEEP;
	}
}
