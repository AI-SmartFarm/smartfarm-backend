package com.smartfarm.backend.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진 파일을 저장할 디렉터리와 사진 1장 한도. 서버에서는 컨테이너가 바뀌어도 남도록 볼륨을 붙인다(compose.prod.yaml).
 * 한도 10MB는 API-004 명세의 "최대 10 MB"다.
 */
@ConfigurationProperties(prefix = "image")
public record ImageProperties(
		@DefaultValue("data/images") String storageDir,
		@DefaultValue("10485760") int maxBytes) {
}
