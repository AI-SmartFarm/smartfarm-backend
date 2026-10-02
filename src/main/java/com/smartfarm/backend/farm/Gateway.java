package com.smartfarm.backend.farm;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** ERD `gateway`. JWT를 발급받는 기기 단위이며, 지금은 Unity 시뮬레이터 1대다. */
@Entity
@Table(name = "gateway")
public class Gateway {

	public static final String ACTIVE = "ACTIVE";
	public static final String INACTIVE = "INACTIVE";

	// 기기가 몇 초마다 요청하므로 매번 쓰지 않고 이 간격이 지났을 때만 마지막 접속 시각을 갱신한다.
	private static final Duration LAST_SEEN_UPDATE_INTERVAL = Duration.ofSeconds(30);

	@Id
	@Column(length = 50)
	private String gatewayId;

	@Column(nullable = false, length = 50)
	private String farmId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 20)
	private String gatewayType;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false, length = 100)
	private String credentialHash;

	private LocalDateTime lastSeenAt;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected Gateway() {
	}

	public Gateway(String gatewayId, String farmId, String name, String gatewayType, String credentialHash) {
		this.gatewayId = gatewayId;
		this.farmId = farmId;
		this.name = name;
		this.gatewayType = gatewayType;
		this.status = ACTIVE;
		this.credentialHash = credentialHash;
		this.createdAt = LocalDateTime.now();
	}

	public boolean isActive() {
		return ACTIVE.equals(status);
	}

	/** 갱신했으면 true. 호출한 쪽이 저장 여부를 판단할 수 있게 돌려준다. */
	public boolean touch(LocalDateTime now) {
		if (lastSeenAt != null && Duration.between(lastSeenAt, now).compareTo(LAST_SEEN_UPDATE_INTERVAL) < 0) {
			return false;
		}
		lastSeenAt = now;
		return true;
	}

	public String getGatewayId() {
		return gatewayId;
	}

	public String getFarmId() {
		return farmId;
	}

	public String getStatus() {
		return status;
	}

	public String getCredentialHash() {
		return credentialHash;
	}

	public LocalDateTime getLastSeenAt() {
		return lastSeenAt;
	}
}
