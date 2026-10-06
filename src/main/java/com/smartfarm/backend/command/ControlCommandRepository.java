package com.smartfarm.backend.command;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlCommandRepository extends JpaRepository<ControlCommand, String> {

	List<ControlCommand> findByFarmIdAndStatusInOrderByCreatedAtAsc(String farmId, Collection<String> statuses);

	Optional<ControlCommand> findFirstByFarmIdAndActuatorOrderByCreatedAtDesc(String farmId, String actuator);

	Optional<ControlCommand> findByCommandIdAndFarmId(String commandId, String farmId);
}
