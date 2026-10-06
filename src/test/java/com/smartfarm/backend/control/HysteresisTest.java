package com.smartfarm.backend.control;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.smartfarm.backend.control.Hysteresis.Decision;

class HysteresisTest {

	// 순환팬: 27℃ 이상이면 켜고 25℃ 이하이면 끈다
	private static Decision fan(double temp, boolean on) {
		return Hysteresis.whenHigh(temp, 27, 25, on);
	}

	// 관수: 30% 이하이면 켜고 50% 이상이면 끈다
	private static Decision pump(double soil, boolean on) {
		return Hysteresis.whenLow(soil, 30, 50, on);
	}

	@Test
	void 켜는_기준에_닿으면_켠다() {
		assertThat(fan(27.0, false)).isEqualTo(Decision.TURN_ON);
		assertThat(pump(30.0, false)).isEqualTo(Decision.TURN_ON);
	}

	@Test
	void 끄는_기준에_닿으면_끈다() {
		assertThat(fan(25.0, true)).isEqualTo(Decision.TURN_OFF);
		assertThat(pump(50.0, true)).isEqualTo(Decision.TURN_OFF);
	}

	@Test
	void 두_기준_사이에서는_지금_상태를_유지한다() {
		assertThat(fan(26.0, true)).isEqualTo(Decision.KEEP);
		assertThat(fan(26.0, false)).isEqualTo(Decision.KEEP);
		assertThat(pump(40.0, true)).isEqualTo(Decision.KEEP);
		assertThat(pump(40.0, false)).isEqualTo(Decision.KEEP);
	}

	@Test
	void 이미_원하는_상태면_명령을_내지_않는다() {
		assertThat(fan(30.0, true)).isEqualTo(Decision.KEEP);
		assertThat(fan(20.0, false)).isEqualTo(Decision.KEEP);
		assertThat(pump(10.0, true)).isEqualTo(Decision.KEEP);
		assertThat(pump(70.0, false)).isEqualTo(Decision.KEEP);
	}
}
