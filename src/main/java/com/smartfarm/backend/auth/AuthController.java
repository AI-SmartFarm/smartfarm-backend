package com.smartfarm.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartfarm.backend.common.ApiError;
import com.smartfarm.backend.farm.Gateway;
import com.smartfarm.backend.farm.GatewayRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** API-008 기기 토큰 발급. 재발급 토큰은 두지 않고, 만료되면 기기가 비밀값으로 다시 발급받는다. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final GatewayRepository gatewayRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService tokenService;

	public AuthController(GatewayRepository gatewayRepository, PasswordEncoder passwordEncoder,
			JwtTokenService tokenService) {
		this.gatewayRepository = gatewayRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	public record TokenRequest(@NotBlank String gatewayId, @NotBlank String secret) {
	}

	public record TokenResponse(String accessToken, String tokenType, long expiresIn, String farmId) {
	}

	@PostMapping("/token")
	public ResponseEntity<?> issue(@Valid @RequestBody TokenRequest request) {
		Gateway gateway = gatewayRepository.findById(request.gatewayId()).orElse(null);
		// 없는 기기와 틀린 비밀값을 같은 응답으로 돌려줘 기기 ID가 존재하는지 알 수 없게 한다.
		if (gateway == null || !passwordEncoder.matches(request.secret(), gateway.getCredentialHash())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("invalid credentials"));
		}
		if (!gateway.isActive()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("gateway inactive"));
		}
		String token = tokenService.issue(gateway.getGatewayId(), gateway.getFarmId());
		return ResponseEntity.ok(new TokenResponse(token, "Bearer", tokenService.expiresInSeconds(), gateway.getFarmId()));
	}
}
