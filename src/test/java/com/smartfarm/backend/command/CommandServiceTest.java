package com.smartfarm.backend.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CommandServiceTest {

	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 13, 0, 0);
	private static final Clock LATER = Clock.fixed(Instant.parse("2026-10-13T00:01:30Z"), ZoneOffset.UTC);

	@Test
	void expiredUnpolledPestCommandDoesNotPreventRetry() {
		ControlCommand old = ControlCommand.pestResponse("old", "farm", "circFan", 1L, 3600, CREATED,
				CREATED.plusSeconds(60));
		ControlCommandRepository repository = mock(ControlCommandRepository.class);
		when(repository.findFirstByFarmIdAndActuatorAndSourceOrderByCreatedAtDesc("farm", "circFan", ControlCommand.SOURCE_AI))
				.thenReturn(Optional.of(old));
		CommandService service = new CommandService(repository, new CommandProperties(60, 30), LATER);
		assertThat(service.isHeldOn("farm", "circFan")).isFalse();
		assertThat(service.enqueuePestResponse("farm", "circFan", 2L, 3600)).isTrue();
		verify(repository).save(any(ControlCommand.class));
	}

	@Test
	void acknowledgeExpiresWithoutAnotherPoll() {
		ControlCommand old = ControlCommand.pestResponse("old", "farm", "circFan", 1L, 3600, CREATED,
				CREATED.plusSeconds(60));
		old.markDelivered(CREATED.plusSeconds(2));
		ControlCommandRepository repository = mock(ControlCommandRepository.class);
		when(repository.findByCommandIdAndFarmId("old", "farm")).thenReturn(Optional.of(old));
		new CommandService(repository, new CommandProperties(60, 30), LATER).acknowledge("farm", "old", true);
		assertThat(old.getStatus()).isEqualTo(ControlCommand.EXPIRED);
	}
}
