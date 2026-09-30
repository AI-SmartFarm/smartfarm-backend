package com.smartfarm.backend.ai;

import java.util.Comparator;
import java.util.List;

/**
 * AI 응답(DiagnosisResponse)을 팀 ERD `diagnosis` 테이블과 API 초안 형식으로 옮긴 결과.
 * 사진 1장당 진단 1건이므로 병이 여러 개 탐지되면 신뢰도가 가장 높은 병 하나만 고른다.
 *
 * <p>중증도는 AI가 초기/중기/말기 3단계로 준다. ERD의 severity(DOUBLE)로 바꾸는 규칙이 아직 없어서
 * 숫자로 바꾸지 않고 단계와 risk_code를 그대로 둔다.
 */
public record DiagnosisResult(
		boolean infected,
		String diseaseCode,
		String diseaseName,
		Double confidence,
		String severityLevel,
		Integer severityRiskCode,
		boolean severityLowConfidence,
		List<Box> boxes) {

	/** API 초안의 boxes 형식. AI의 [x0, y0, x1, y1]에서 바꾼다. */
	public record Box(double x, double y, double width, double height) {

		static Box fromXyxy(List<Double> xyxy) {
			double x0 = xyxy.get(0);
			double y0 = xyxy.get(1);
			return new Box(x0, y0, xyxy.get(2) - x0, xyxy.get(3) - y0);
		}
	}

	public static DiagnosisResult from(DiagnosisResponse response) {
		List<Detection> detections = response.detections();

		Detection top = detections.stream()
				.filter(d -> !DiseaseCatalog.isNormal(d.className()))
				.max(Comparator.comparingDouble(Detection::confidence))
				.orElse(null);

		if (top == null) {
			Double confidence = detections.stream()
					.map(Detection::confidence)
					.max(Comparator.naturalOrder())
					.orElse(null);
			return new DiagnosisResult(false, null, null, confidence, null, null, false, List.of());
		}

		DiseaseCatalog.Disease disease = DiseaseCatalog.byClass(top.className());
		List<Box> boxes = detections.stream()
				.filter(d -> d.className().equals(top.className()))
				.map(d -> Box.fromXyxy(d.bbox()))
				.toList();
		Severity severity = top.severity();
		return new DiagnosisResult(
				true,
				disease.label(),
				disease.name(),
				top.confidence(),
				severity == null ? null : severity.level(),
				severity == null ? null : severity.riskCode(),
				severity != null && severity.lowConfidence(),
				boxes);
	}
}
