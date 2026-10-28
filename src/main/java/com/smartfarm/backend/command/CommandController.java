package com.smartfarm.backend.command;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartfarm.backend.common.BadRequestException;
import com.smartfarm.backend.common.ClockConfig;
import com.smartfarm.backend.common.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** API-003 장치 제어 명령 전달. 시뮬레이터가 주기적으로 조회하고 처리 결과를 보고한다. JWT 검사는 GatewayAuthFilter가 한다. */
@RestController
@RequestMapping("/api/v1/farms/{farmId}/commands")
@Tag(name = OpenApiConfig.TAG_DEVICE)
@SecurityRequirement(name = OpenApiConfig.DEVICE_JWT)
public class CommandController {

	private final CommandService commandService;

	public CommandController(CommandService commandService) {
		this.commandService = commandService;
	}

	public record CommandView(String id, String actuator, String action, String reason, String createdAt) {

		static CommandView from(ControlCommand command) {
			return new CommandView(command.getCommandId(), command.getActuator(), command.getAction(),
					command.getReason(), command.getCreatedAt().atZone(ClockConfig.ZONE).toOffsetDateTime().toString());
		}
	}

	public record AckRequest(@NotBlank String id, @NotBlank String status) {
	}

	@Operation(summary = "API-003 대기 중인 제어 명령 조회", description = "ACK를 받지 못한 명령을 모두 내려준다. ACK가 올 때까지 조회할 때마다 다시 내려주고, 60초가 지나면 만료한다.")
	@GetMapping
	public Map<String, List<CommandView>> poll(@PathVariable String farmId) {
		return Map.of("commands", commandService.pollPending(farmId).stream().map(CommandView::from).toList());
	}

	@Operation(summary = "API-003 명령 처리 결과 보고(ACK)", description = "status는 applied 또는 rejected. 그 밖의 값이면 400. ACK된 명령은 다시 내려주지 않는다.")
	@PostMapping("/ack")
	public Map<String, Boolean> ack(@PathVariable String farmId, @Valid @RequestBody AckRequest request) {
		boolean applied = switch (request.status()) {
			case "applied" -> true;
			case "rejected" -> false;
			default -> throw new BadRequestException("status must be applied or rejected");
		};
		commandService.acknowledge(farmId, request.id(), applied);
		return Map.of("ok", true);
	}
}
