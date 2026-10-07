package com.smartfarm.backend.command;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-003. 제어 명령을 대기열에 넣고, 시뮬레이터가 가져갈 때 내려주고, 결과 보고(ACK)를 반영한다. */
@Service
public class CommandService {

	private static final List<String> AWAITING_ACK = List.of(ControlCommand.PENDING, ControlCommand.DELIVERED);

	private final ControlCommandRepository commandRepository;
	private final CommandProperties properties;
	private final Clock clock;

	public CommandService(ControlCommandRepository commandRepository, CommandProperties properties, Clock clock) {
		this.commandRepository = commandRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 같은 장치에 결과를 기다리는 명령이 있거나 직전 명령을 낸 지 얼마 안 됐으면 새 명령을 만들지 않는다.
	 * 만들었으면 true.
	 */
	@Transactional
	public boolean enqueueIfIdle(String farmId, String actuator, String action, String source, String reason,
			Double triggerValue) {
		LocalDateTime now = LocalDateTime.now(clock);
		ControlCommand latest = commandRepository.findFirstByFarmIdAndActuatorOrderByCreatedAtDesc(farmId, actuator)
				.orElse(null);
		if (latest != null) {
			boolean inFlight = latest.isAwaitingAck() && !latest.isExpiredAt(now);
			boolean coolingDown = Duration.between(latest.getCreatedAt(), now)
					.compareTo(Duration.ofSeconds(properties.cooldownSeconds())) < 0;
			if (inFlight || coolingDown) {
				return false;
			}
		}
		String id = "cmd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		commandRepository.save(new ControlCommand(id, farmId, actuator, action, source, reason, triggerValue, now,
				now.plusSeconds(properties.ttlSeconds())));
		return true;
	}

	/**
	 * 병해 대응 켜기 명령. 이미 유지 중인 병해 대응이 있으면 새로 만들지 않고 그 유지 시간을 그대로 둔다.
	 * 평소 제어와 달리 쿨다운을 보지 않는다. 병해 대응이 온도 제어 때문에 밀리면 안 되기 때문이다. 만들었으면 true.
	 */
	@Transactional
	public boolean enqueuePestResponse(String farmId, String actuator, long diagnosisId, int durationSec) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (activeHold(farmId, actuator, now).isPresent()) {
			return false;
		}
		String id = "cmd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		commandRepository.save(ControlCommand.pestResponse(id, farmId, actuator, diagnosisId, durationSec, now,
				now.plusSeconds(properties.ttlSeconds())));
		return true;
	}

	/** 병해 대응으로 켜 둔 장치가 아직 유지 시간 안인지. */
	@Transactional(readOnly = true)
	public boolean isHeldOn(String farmId, String actuator) {
		return activeHold(farmId, actuator, LocalDateTime.now(clock)).isPresent();
	}

	/** 장치의 가장 최근 명령이 유지 시간이 끝난 병해 대응 켜기인지. 끝난 뒤 장치를 끌지 판단할 때 쓴다. */
	@Transactional(readOnly = true)
	public boolean lastWasEndedPestHold(String farmId, String actuator) {
		LocalDateTime now = LocalDateTime.now(clock);
		return commandRepository.findFirstByFarmIdAndActuatorOrderByCreatedAtDesc(farmId, actuator)
				.filter(c -> ControlCommand.SOURCE_AI.equals(c.getSource()) && "on".equals(c.getAction()))
				.filter(c -> !c.holdsOnAt(now))
				.isPresent();
	}

	@Transactional(readOnly = true)
	public List<ControlCommand> findByDiagnoses(Collection<Long> diagnosisIds) {
		return diagnosisIds.isEmpty() ? List.of() : commandRepository.findByDiagnosisIdInOrderByCreatedAtAsc(diagnosisIds);
	}

	private Optional<ControlCommand> activeHold(String farmId, String actuator, LocalDateTime now) {
		return commandRepository.findFirstByFarmIdAndActuatorAndSourceOrderByCreatedAtDesc(farmId, actuator,
				ControlCommand.SOURCE_AI).filter(c -> c.holdsOnAt(now));
	}

	/** 결과 보고를 받지 못한 명령을 모두 내려준다. ACK가 유실됐을 수 있어 받을 때까지 매번 다시 내려준다. */
	@Transactional
	public List<ControlCommand> pollPending(String farmId) {
		LocalDateTime now = LocalDateTime.now(clock);
		List<ControlCommand> pending = new ArrayList<>();
		for (ControlCommand command : commandRepository.findByFarmIdAndStatusInOrderByCreatedAtAsc(farmId,
				AWAITING_ACK)) {
			if (command.isExpiredAt(now)) {
				command.markExpired();
				continue;
			}
			command.markDelivered(now);
			pending.add(command);
		}
		return pending;
	}

	/** 모르는 명령 ID는 무시한다. 만료된 뒤 늦게 도착한 ACK도 상태를 바꾸지 않는다. */
	@Transactional
	public void acknowledge(String farmId, String commandId, boolean applied) {
		commandRepository.findByCommandIdAndFarmId(commandId, farmId)
				.ifPresent(command -> command.acknowledge(applied, LocalDateTime.now(clock)));
	}
}
