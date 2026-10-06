package com.smartfarm.backend.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ControlCommandTest {

	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 2, 14, 0, 0);

	private static ControlCommand command() {
		return new ControlCommand("cmd_test", "greenhouse-01", "circFan", "on", ControlCommand.SOURCE_AUTO,
				"TEMP_HIGH", 28.0, CREATED, CREATED.plusSeconds(60));
	}

	@Test
	void 내려준_뒤에도_결과_보고_전까지는_대기_상태다() {
		ControlCommand command = command();

		command.markDelivered(CREATED.plusSeconds(3));

		assertThat(command.getStatus()).isEqualTo(ControlCommand.DELIVERED);
		assertThat(command.isAwaitingAck()).isTrue();
	}

	@Test
	void 결과_보고에_따라_상태가_정해진다() {
		ControlCommand applied = command();
		applied.acknowledge(true, CREATED.plusSeconds(4));
		ControlCommand rejected = command();
		rejected.acknowledge(false, CREATED.plusSeconds(4));

		assertThat(applied.getStatus()).isEqualTo(ControlCommand.ACKED);
		assertThat(rejected.getStatus()).isEqualTo(ControlCommand.REJECTED);
		assertThat(applied.isAwaitingAck()).isFalse();
	}

	@Test
	void 같은_결과_보고가_다시_와도_처음_결과를_유지한다() {
		ControlCommand command = command();
		command.acknowledge(true, CREATED.plusSeconds(4));

		command.acknowledge(false, CREATED.plusSeconds(8));

		assertThat(command.getStatus()).isEqualTo(ControlCommand.ACKED);
	}

	@Test
	void 유효_시간이_지나면_만료된_것으로_본다() {
		ControlCommand command = command();

		assertThat(command.isExpiredAt(CREATED.plusSeconds(60))).isFalse();
		assertThat(command.isExpiredAt(CREATED.plusSeconds(61))).isTrue();
	}

	@Test
	void 만료된_뒤에_온_결과_보고는_상태를_바꾸지_않는다() {
		ControlCommand command = command();
		command.markExpired();

		command.acknowledge(true, CREATED.plusSeconds(90));

		assertThat(command.getStatus()).isEqualTo(ControlCommand.EXPIRED);
	}
}
