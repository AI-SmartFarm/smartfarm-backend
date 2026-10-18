package com.smartfarm.backend.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DiagnosisResponseValidationTest {

	static Stream<DiagnosisResponse> invalidResponses() {
		Detection valid = new Detection("tomato_disease18", 0.9, List.of(0.0, 0.0, 10.0, 10.0), null, null);
		return Stream.of(
				new DiagnosisResponse("error", "tomato", null, null, null, null),
				new DiagnosisResponse("detected", "tomato", null, null, null, null),
				new DiagnosisResponse("no_detection", "tomato", null, null, List.of(valid), null),
				detected(null),
				detected(new Detection(null, 0.9, valid.bbox(), null, null)),
				detected(new Detection("", 0.9, valid.bbox(), null, null)),
				detected(new Detection("tomato_disease18", Double.NaN, valid.bbox(), null, null)),
				detected(new Detection("tomato_disease18", 1.1, valid.bbox(), null, null)),
				detected(new Detection("tomato_disease18", 0.9, null, null, null)),
				detected(new Detection("tomato_disease18", 0.9, List.of(0.0, 0.0, 1.0), null, null)),
				detected(new Detection("tomato_disease18", 0.9, Arrays.asList(0.0, null, 1.0, 1.0), null, null)),
				detected(new Detection("tomato_disease18", 0.9, List.of(0.0, 0.0, Double.POSITIVE_INFINITY, 1.0), null, null)),
				detected(new Detection("tomato_disease18", 0.9, List.of(10.0, 0.0, 0.0, 1.0), null, null)));
	}

	private static DiagnosisResponse detected(Detection detection) {
		return new DiagnosisResponse("detected", "tomato", null, null, Arrays.asList(detection), "test");
	}

	@ParameterizedTest
	@MethodSource("invalidResponses")
	void invalidPayloadsUseTheDiagnosisFailureException(DiagnosisResponse response) {
		assertThatThrownBy(() -> DiagnosisResult.from(response)).isInstanceOf(IllegalStateException.class);
	}
}
