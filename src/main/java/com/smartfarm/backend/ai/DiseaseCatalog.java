package com.smartfarm.backend.ai;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 모델 클래스(tomato_disease18)와 팀 라벨(API-004 groundTruthPestLabel, tomato-A)의 대응표.
 * 질병 코드→병 이름은 AIHub 공식 코드표 없이 추정한 것이며, 시뮬레이터의 병 이름 9개와 모두 일치한다.
 */
public final class DiseaseCatalog {

	public record Disease(String modelClass, String label, String name) {
	}

	private static final Map<String, Disease> BY_CLASS = Stream.of(
			new Disease("tomato_disease18", "tomato-A", "잎곰팡이병"),
			new Disease("tomato_disease19", "tomato-B", "황화잎말이바이러스"),
			new Disease("pepper_disease3", "pepper-A", "고추마일드모틀바이러스"),
			new Disease("pepper_disease4", "pepper-B", "고추점무늬병"),
			new Disease("cucumber_disease15", "cucumber-A", "모자이크바이러스"),
			new Disease("strawberry_disease7", "strawberry-A", "잿빛곰팡이병"),
			new Disease("strawberry_disease8", "strawberry-B", "흰가루병"),
			new Disease("lettuce_disease9", "lettuce-A", "균핵병"),
			new Disease("lettuce_disease10", "lettuce-B", "노균병"))
			.collect(Collectors.toUnmodifiableMap(Disease::modelClass, Function.identity()));

	private static final Set<String> AI_CROPS = Set.of("tomato", "pepper", "cucumber", "strawberry", "lettuce");

	private DiseaseCatalog() {
	}

	public static Collection<Disease> all() {
		return BY_CLASS.values();
	}

	public static boolean isNormal(String modelClass) {
		return modelClass.endsWith("_normal");
	}

	/** 목록에 없는 병 클래스는 정상으로 뭉개지 않고 실패시킨다 (모델이 새 클래스로 재학습됐다는 뜻이므로). */
	public static Disease byClass(String modelClass) {
		Disease disease = BY_CLASS.get(modelClass);
		if (disease == null) {
			throw new IllegalStateException("AI가 대응표에 없는 클래스를 반환했다: " + modelClass);
		}
		return disease;
	}

	/** API-004 species(Tomato)를 AI 서버 crop(tomato)으로 바꾼다. species가 none이면 진단 대상이 아니다. */
	public static String aiCrop(String species) {
		String crop = species == null ? "" : species.toLowerCase(Locale.ROOT);
		if (!AI_CROPS.contains(crop)) {
			throw new IllegalArgumentException("진단할 수 없는 species: " + species);
		}
		return crop;
	}
}
