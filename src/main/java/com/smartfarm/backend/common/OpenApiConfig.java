package com.smartfarm.backend.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Swagger UI(/swagger-ui.html)와 OpenAPI 문서(/v3/api-docs) 설정.
 * 서버 주소는 상대 경로로 둔다. 테스트 서버는 Cloudflare 임시 터널이라 주소가 바뀔 수 있어서, 연 페이지의 주소로 바로 호출되게 한다.
 */
@Configuration
public class OpenApiConfig {

	/** 기기(시뮬레이터) API에 붙이는 보안 요구사항 이름. 컨트롤러의 @SecurityRequirement와 같아야 한다. */
	public static final String DEVICE_JWT = "deviceJwt";

	public static final String TAG_MOBILE = "모바일 앱";
	public static final String TAG_DEVICE = "기기(시뮬레이터)";
	public static final String TAG_AUTH = "기기 인증";
	public static final String TAG_OPS = "운영 확인";

	@Bean
	public OpenAPI smartfarmOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("GrowCare 스마트팜 백엔드 API")
						.version("v1")
						.description("""
								모바일 앱 API는 인증이 없다(사용자 로그인 없음).
								기기(시뮬레이터) API는 기기 토큰(JWT)이 필요하다. `POST /api/v1/auth/token`으로 받은 accessToken을 \
								오른쪽 위 Authorize에 넣으면 된다.
								시각은 한국 시간(+09:00) ISO-8601이고, 오류 본문은 `{"error": "설명"}`이다."""))
				.addServersItem(new Server().url("/").description("지금 연 서버"))
				.components(new Components().addSecuritySchemes(DEVICE_JWT, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("API-009에서 발급한 기기 토큰. 유효 시간 1시간")))
				.addTagsItem(new Tag().name(TAG_MOBILE).description("앱이 호출하는 조회 API. 인증 없음"))
				.addTagsItem(new Tag().name(TAG_DEVICE).description("시뮬레이터가 호출하는 API. 기기 토큰 필요"))
				.addTagsItem(new Tag().name(TAG_AUTH).description("기기 토큰 발급"))
				.addTagsItem(new Tag().name(TAG_OPS).description("배포 서버 상태 확인용"));
	}
}
