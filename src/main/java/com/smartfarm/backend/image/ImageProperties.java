package com.smartfarm.backend.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진 파일을 저장할 디렉터리와 사진 1장 한도. 서버에서는 컨테이너가 바뀌어도 남도록 볼륨을 붙인다(compose.prod.yaml).
 * 한도 10MB는 API-004 명세의 "최대 10 MB"다.
 * routineIntervalSeconds: 주기(routine) 사진은 카메라별로 이 간격마다 1장만 저장·진단한다.
 * 식물마다 카메라가 있어 모두 저장하면 디스크와 AI 처리량이 버티지 못하기 때문이다(ERD 9-2).
 */
@ConfigurationProperties(prefix = "image")
public record ImageProperties(
		@DefaultValue("data/images") String storageDir,
		@DefaultValue("10485760") int maxBytes,
		@DefaultValue("600") long routineIntervalSeconds) {
}
