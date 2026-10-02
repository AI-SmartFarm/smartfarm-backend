package com.smartfarm.backend.farm;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** ERD `farm`. 시뮬레이터 SimConfig.farmId와 같은 값을 ID로 쓴다. */
@Entity
@Table(name = "farm")
public class Farm {

	@Id
	@Column(length = 50)
	private String farmId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected Farm() {
	}

	public Farm(String farmId, String name) {
		this.farmId = farmId;
		this.name = name;
		this.createdAt = LocalDateTime.now();
	}

	public String getFarmId() {
		return farmId;
	}

	public String getName() {
		return name;
	}
}
