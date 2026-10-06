package com.smartfarm.backend.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.smartfarm.backend.common.ApiError;
import com.smartfarm.backend.farm.Gateway;
import com.smartfarm.backend.farm.GatewayRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 시뮬레이터(기기)가 부르는 API에만 JWT를 요구한다. 모바일 API는 로그인이 없어 검사하지 않는다(9/23 결정).
 * 검증 순서는 API 명세 공통 규칙과 같다: 토큰 → 농장 일치(403) → 기기 활성 상태(403).
 */
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

	private record Protected(String method, PathPattern pattern) {
	}

	private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

	// API-001 텔레메트리, API-004 이미지, API-003 명령 조회와 결과 보고
	private static final List<Protected> PROTECTED = List.of(
			new Protected("POST", PARSER.parse("/api/v1/farms/{farmId}/telemetry")),
			new Protected("POST", PARSER.parse("/api/v1/farms/{farmId}/images")),
			new Protected("GET", PARSER.parse("/api/v1/farms/{farmId}/commands")),
			new Protected("POST", PARSER.parse("/api/v1/farms/{farmId}/commands/ack")));

	private final JwtTokenService tokenService;
	private final GatewayRepository gatewayRepository;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public GatewayAuthFilter(JwtTokenService tokenService, GatewayRepository gatewayRepository, JsonMapper jsonMapper,
			Clock clock) {
		this.tokenService = tokenService;
		this.gatewayRepository = gatewayRepository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return pathFarmId(request) == null;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			reject(response, HttpStatus.UNAUTHORIZED, new InvalidTokenException().getMessage());
			return;
		}

		GatewayPrincipal principal;
		try {
			principal = tokenService.verify(header.substring("Bearer ".length()).trim());
		}
		catch (InvalidTokenException e) {
			reject(response, HttpStatus.UNAUTHORIZED, e.getMessage());
			return;
		}

		if (!principal.farmId().equals(pathFarmId(request))) {
			reject(response, HttpStatus.FORBIDDEN, "farm access denied");
			return;
		}

		// 토큰이 유효해도 기기를 비활성으로 바꾸면 즉시 막을 수 있게 매 요청 DB 상태를 본다.
		Gateway gateway = gatewayRepository.findById(principal.gatewayId()).orElse(null);
		if (gateway == null || !gateway.isActive()) {
			reject(response, HttpStatus.FORBIDDEN, "gateway inactive");
			return;
		}
		if (gateway.touch(LocalDateTime.now(clock))) {
			gatewayRepository.save(gateway);
		}

		request.setAttribute(GatewayPrincipal.REQUEST_ATTRIBUTE, principal);
		chain.doFilter(request, response);
	}

	private static String pathFarmId(HttpServletRequest request) {
		PathContainer path = PathContainer.parsePath(request.getRequestURI());
		for (Protected p : PROTECTED) {
			if (!p.method().equalsIgnoreCase(request.getMethod())) {
				continue;
			}
			PathPattern.PathMatchInfo match = p.pattern().matchAndExtract(path);
			if (match != null) {
				return match.getUriVariables().get("farmId");
			}
		}
		return null;
	}

	private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(jsonMapper.writeValueAsString(new ApiError(message)));
	}
}
