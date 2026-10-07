package com.smartfarm.backend.command;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * ERD `control_command`. 명령 대기열(API-003), 제어 이력, 자동 대응 이력을 겸한다.
 * 원인(reason)과 판단 당시 값(triggerValue)을 함께 남겨 앱의 활동 로그를 이 테이블만으로 만들 수 있게 한다.
 */
@Entity
@Table(name = "control_command", indexes = {
		@Index(name = "idx_command_farm_status", columnList = "farmId, status, createdAt"),
		@Index(name = "idx_command_farm_time", columnList = "farmId, createdAt") })
public class ControlCommand {

	public static final String PENDING = "PENDING";
	public static final String DELIVERED = "DELIVERED";
	public static final String ACKED = "ACKED";
	public static final String REJECTED = "REJECTED";
	public static final String EXPIRED = "EXPIRED";

	public static final String SOURCE_AUTO = "AUTO";
	public static final String SOURCE_AI = "AI";

	@Id
	@Column(length = 40)
	private String commandId;

	@Column(nullable = false, length = 50)
	private String farmId;

	private Long diagnosisId;

	@Column(nullable = false, length = 30)
	private String actuator;

	@Column(nullable = false, length = 10)
	private String action;

	private Integer durationSec;

	@Column(nullable = false, length = 20)
	private String source;

	@Column(nullable = false, length = 30)
	private String reason;

	private Double triggerValue;

	@Column(nullable = false, length = 20)
	private String status;

	private Boolean ackAccepted;

	@Column(length = 255)
	private String ackReason;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime deliveredAt;

	private LocalDateTime ackedAt;

	private LocalDateTime expiresAt;

	protected ControlCommand() {
	}

	public ControlCommand(String commandId, String farmId, String actuator, String action, String source,
			String reason, Double triggerValue, LocalDateTime createdAt, LocalDateTime expiresAt) {
		this.commandId = commandId;
		this.farmId = farmId;
		this.actuator = actuator;
		this.action = action;
		this.source = source;
		this.reason = reason;
		this.triggerValue = triggerValue;
		this.status = PENDING;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** 병해 대응 명령. 원인 진단과 가동 시간을 함께 남겨 진단 이력(API-008)에서 대응 내역을 보여 줄 수 있게 한다. */
	public static ControlCommand pestResponse(String commandId, String farmId, String actuator, long diagnosisId,
			int durationSec, LocalDateTime createdAt, LocalDateTime expiresAt) {
		ControlCommand command = new ControlCommand(commandId, farmId, actuator, "on", SOURCE_AI, "PEST_RESPONSE",
				null, createdAt, expiresAt);
		command.diagnosisId = diagnosisId;
		command.durationSec = durationSec;
		return command;
	}

	/** 가동 시간이 정해진 켜기 명령이 아직 유효한지. 거절·만료된 명령은 장치를 켜지 못했으므로 유지 시간으로 보지 않는다. */
	public boolean holdsOnAt(LocalDateTime now) {
		boolean effective = !REJECTED.equals(status) && !EXPIRED.equals(status);
		return effective && "on".equals(action) && durationSec != null
				&& now.isBefore(createdAt.plusSeconds(durationSec));
	}

	/** 아직 시뮬레이터의 결과 보고를 받지 못한 상태. 이 동안에는 조회할 때마다 다시 내려준다. */
	public boolean isAwaitingAck() {
		return PENDING.equals(status) || DELIVERED.equals(status);
	}

	public boolean isExpiredAt(LocalDateTime now) {
		return expiresAt != null && now.isAfter(expiresAt);
	}

	public void markDelivered(LocalDateTime now) {
		if (PENDING.equals(status)) {
			status = DELIVERED;
			deliveredAt = now;
		}
	}

	public void markExpired() {
		status = EXPIRED;
	}

	/** 이미 결과를 받은 명령에 같은 ACK가 다시 와도 처음 결과를 유지한다(시뮬레이터가 ACK를 재전송할 수 있다). */
	public void acknowledge(boolean applied, LocalDateTime now) {
		if (!isAwaitingAck()) {
			return;
		}
		status = applied ? ACKED : REJECTED;
		ackAccepted = applied;
		ackReason = applied ? "applied" : "rejected";
		ackedAt = now;
	}

	public String getCommandId() {
		return commandId;
	}

	public String getFarmId() {
		return farmId;
	}

	public String getActuator() {
		return actuator;
	}

	public String getAction() {
		return action;
	}

	public String getReason() {
		return reason;
	}

	public Double getTriggerValue() {
		return triggerValue;
	}

	public String getSource() {
		return source;
	}

	public Long getDiagnosisId() {
		return diagnosisId;
	}

	public Integer getDurationSec() {
		return durationSec;
	}

	public String getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
