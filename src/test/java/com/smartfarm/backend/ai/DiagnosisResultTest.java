package com.smartfarm.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class DiagnosisResultTest {

	private static Detection detection(String className, double confidence, List<Double> bbox, Severity severity) {
		return new Detection(className, confidence, bbox, severity, null);
	}

	private static DiagnosisResponse detected(Detection... detections) {
		return new DiagnosisResponse("detected", "tomato", null, null, List.of(detections));
	}

	@Test
	void convertsDiseaseToTeamLabelAndBox() {
		Severity severity = new Severity("중기", 2, 0.81, 0.6266, false);
		DiagnosisResult result = DiagnosisResult.from(detected(
				detection("tomato_disease18", 0.93, List.of(120.0, 80.0, 300.0, 260.0), severity)));

		assertThat(result.infected()).isTrue();
		assertThat(result.diseaseCode()).isEqualTo("tomato-A");
		assertThat(result.diseaseName()).isEqualTo("잎곰팡이병");
		assertThat(result.confidence()).isEqualTo(0.93);
		assertThat(result.severityLevel()).isEqualTo("중기");
		assertThat(result.severityRiskCode()).isEqualTo(2);
		assertThat(result.severityLowConfidence()).isFalse();
		assertThat(result.boxes()).containsExactly(new DiagnosisResult.Box(120.0, 80.0, 180.0, 180.0));
	}

	@Test
	void keepsLowConfidenceFlag() {
		Severity severity = new Severity("말기", 3, 0.96, 0.4714, true);
		DiagnosisResult result = DiagnosisResult.from(detected(
				detection("pepper_disease4", 0.9, List.of(0.0, 0.0, 10.0, 10.0), severity)));

		assertThat(result.diseaseCode()).isEqualTo("pepper-B");
		assertThat(result.severityLowConfidence()).isTrue();
	}

	@Test
	void picksHighestConfidenceDiseaseAndItsBoxesOnly() {
		DiagnosisResult result = DiagnosisResult.from(detected(
				detection("tomato_disease18", 0.5, List.of(0.0, 0.0, 10.0, 10.0), null),
				detection("tomato_disease19", 0.9, List.of(20.0, 20.0, 40.0, 50.0), null),
				detection("tomato_disease19", 0.4, List.of(60.0, 60.0, 70.0, 70.0), null)));

		assertThat(result.diseaseCode()).isEqualTo("tomato-B");
		assertThat(result.confidence()).isEqualTo(0.9);
		assertThat(result.boxes()).hasSize(2);
		assertThat(result.severityLevel()).isNull();
		assertThat(result.severityRiskCode()).isNull();
	}

	@Test
	void diseaseWinsEvenWhenNormalHasHigherConfidence() {
		DiagnosisResult result = DiagnosisResult.from(detected(
				detection("tomato_normal", 0.95, List.of(0.0, 0.0, 500.0, 500.0), null),
				detection("tomato_disease18", 0.6, List.of(10.0, 10.0, 20.0, 20.0), null)));

		assertThat(result.infected()).isTrue();
		assertThat(result.diseaseCode()).isEqualTo("tomato-A");
	}

	@Test
	void normalOnlyIsNotInfected() {
		DiagnosisResult result = DiagnosisResult.from(detected(
				detection("tomato_normal", 0.88, List.of(0.0, 0.0, 500.0, 500.0), null)));

		assertThat(result.infected()).isFalse();
		assertThat(result.diseaseCode()).isNull();
		assertThat(result.diseaseName()).isNull();
		assertThat(result.confidence()).isEqualTo(0.88);
		assertThat(result.boxes()).isEmpty();
	}

	@Test
	void noDetectionIsNotInfectedWithoutConfidence() {
		DiagnosisResult result = DiagnosisResult.from(
				new DiagnosisResponse("no_detection", "tomato", "병징을 찾지 못했습니다", null, null));

		assertThat(result.infected()).isFalse();
		assertThat(result.confidence()).isNull();
	}

	@Test
	void unknownDiseaseClassFailsInsteadOfBeingTreatedAsHealthy() {
		assertThatThrownBy(() -> DiagnosisResult.from(detected(
				detection("tomato_disease99", 0.9, List.of(0.0, 0.0, 1.0, 1.0), null))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("tomato_disease99");
	}

	@Test
	void catalogCoversNineDiseasesWithUniqueLabels() {
		assertThat(DiseaseCatalog.all()).hasSize(9);
		assertThat(DiseaseCatalog.all().stream().map(DiseaseCatalog.Disease::label).distinct()).hasSize(9);
		assertThat(DiseaseCatalog.all()).allSatisfy(d -> assertThat(d.label()).matches("[a-z]+-[AB]"));
	}

	@Test
	void convertsSpeciesToAiCrop() {
		assertThat(DiseaseCatalog.aiCrop("Tomato")).isEqualTo("tomato");
		assertThat(DiseaseCatalog.aiCrop("Strawberry")).isEqualTo("strawberry");
		assertThatThrownBy(() -> DiseaseCatalog.aiCrop("none")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DiseaseCatalog.aiCrop(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
