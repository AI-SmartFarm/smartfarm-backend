package com.smartfarm.backend.ai;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 응답(DiagnosisResponse)을 팀 ERD `diagnosis` 테이블과 API 초안 형식으로 옮긴 결과.
 * 사진 1장당 진단 1건이므로 병이 여러 개 탐지되면 신뢰도가 가장 높은 병 하나만 고른다.
 *
 * <p>중증도는 AI가 초기/중기/말기 3단계로 준다. ERD의 severity(DOUBLE)로 바꾸는 규칙이 아직 없어서
 * 숫자로 바꾸지 않고 단계와 risk_code를 그대로 둔다.
 *
 * <p>guide는 AI 지식베이스의 예방·방제 원칙을 줄바꿈으로 이은 것이다. 병해충 조치 안내는 AI 서버가 맡기로 해서
 * (제안서 아키텍처의 "AI 조치사항 DB") 백엔드가 문구를 만들지 않고 그대로 옮긴다. 근거가 없으면 null이다.
 */
public record DiagnosisResult(
		boolean infected,
		String diseaseCode,
		String diseaseName,
		Double confidence,
		String severityLevel,
		Integer severityRiskCode,
		boolean severityLowConfidence,
		List<Box> boxes,
		String guide) {

	/** API 초안의 boxes 형식. AI의 [x0, y0, x1, y1]에서 바꾼다. */
	public record Box(double x, double y, double width, double height) {

		static Box fromXyxy(List<Double> xyxy) {
			double x0 = xyxy.get(0);
			double y0 = xyxy.get(1);
			return new Box(x0, y0, xyxy.get(2) - x0, xyxy.get(3) - y0);
		}
	}

	public static DiagnosisResult from(DiagnosisResponse response) {
		validate(response);
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
			return new DiagnosisResult(false, null, null, confidence, null, null, false, List.of(), null);
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
				boxes,
				guideOf(top.diagnosis()));
	}

	/** 잘못된 AI 응답은 정상 판정·사진 정리로 진행하지 않고 기존 진단 실패 경로로 보낸다. */
	private static void validate(DiagnosisResponse response) {
		if (response == null || !("detected".equals(response.result()) || "no_detection".equals(response.result()))) {
			throw new IllegalStateException("AI 응답의 result가 올바르지 않음");
		}
		if ((response.isDetected() && response.detections().isEmpty())
				|| (!response.isDetected() && !response.detections().isEmpty())) {
			throw new IllegalStateException("AI 응답의 result와 detections가 일치하지 않음");
		}
		for (Detection detection : response.detections()) {
			if (detection == null || detection.className() == null || detection.className().isBlank()) {
				throw new IllegalStateException("AI 탐지의 class가 없음");
			}
			if (!DiseaseCatalog.isNormal(detection.className())) {
				DiseaseCatalog.byClass(detection.className());
			}
			if (!Double.isFinite(detection.confidence()) || detection.confidence() < 0 || detection.confidence() > 1) {
				throw new IllegalStateException("AI 탐지의 confidence가 올바르지 않음");
			}
			List<Double> bbox = detection.bbox();
			if (bbox == null || bbox.size() != 4 || bbox.stream().anyMatch(v -> v == null || !Double.isFinite(v))
					|| bbox.get(2) < bbox.get(0) || bbox.get(3) < bbox.get(1)) {
				throw new IllegalStateException("AI 탐지의 bbox가 올바르지 않음");
			}
		}
	}

	private static String guideOf(Diagnosis diagnosis) {
		if (diagnosis == null || diagnosis.preventionPrinciples() == null) {
			return null;
		}
		String joined = diagnosis.preventionPrinciples().stream()
				.filter(p -> p != null && !p.isBlank())
				.map(String::strip)
				.collect(Collectors.joining("\n"));
		return joined.isEmpty() ? null : joined;
	}
}
