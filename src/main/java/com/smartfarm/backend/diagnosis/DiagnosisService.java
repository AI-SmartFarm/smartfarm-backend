package com.smartfarm.backend.diagnosis;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.smartfarm.backend.ai.AiDiagnosisClient;
import com.smartfarm.backend.ai.DiagnosisResponse;
import com.smartfarm.backend.ai.DiagnosisResult;
import com.smartfarm.backend.ai.DiseaseCatalog;
import com.smartfarm.backend.control.PestResponseService;
import com.smartfarm.backend.image.CropImage;
import com.smartfarm.backend.image.ImageService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 받은 사진을 AI 서버에 보내(API-005, 동기 호출) 결과를 diagnosis에 저장한다.
 * 평가용 정답(gt_*)은 crop_image에만 있고 AI에는 사진과 작물 이름만 보낸다.
 * 자동 대응(제어 명령·알림)은 disease_response 규칙이 정해지면 여기서 이어 붙인다.
 */
@Service
public class DiagnosisService implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

	private final ImageService imageService;
	private final PestResponseService pestResponseService;
	private final AiDiagnosisClient aiClient;
	private final DiagnosisRepository diagnosisRepository;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final ThreadPoolExecutor executor;

	public DiagnosisService(AiDiagnosisClient aiClient, DiagnosisRepository diagnosisRepository, JsonMapper jsonMapper,
			Clock clock, DiagnosisProperties properties, ImageService imageService, PestResponseService pestResponseService) {
		this.imageService = imageService;
		this.pestResponseService = pestResponseService;
		this.aiClient = aiClient;
		this.diagnosisRepository = diagnosisRepository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		// AI 서버는 GPU 1장에서 돈다. 동시에 여러 장 보내도 빨라지지 않으므로 한 장씩 보낸다.
		this.executor = properties.async()
				? new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(properties.queueCapacity()),
						r -> new Thread(r, "diagnosis"))
				: null;
	}

	/** 사진 저장이 커밋된 뒤 호출한다. 식물이 없거나 AI가 모르는 작물이면 진단하지 않는다. */
	public void request(ImageService.Received received) {
		CropImage image = received.image();
		String crop = aiCrop(image.getSpecies());
		if (crop == null) {
			return;
		}
		// 대기 중에는 메타데이터만 보유하고 실행 시 디스크에서 사진을 읽는다.
		Runnable task = () -> diagnose(image, crop);
		if (executor == null) {
			task.run();
			return;
		}
		try {
			executor.execute(task);
		}
		catch (RejectedExecutionException e) {
			log.warn("진단 대기열이 가득 차 사진 {}은(는) 진단하지 않는다. AI 서버 상태를 확인할 것", image.getImageId());
		}
	}

	public List<Diagnosis> history(String farmId, int size) {
		return diagnosisRepository.findByFarmIdAndDiagnosedAtNotNullOrderByDiagnosedAtDescDiagnosisIdDesc(farmId,
				Limit.of(size));
	}

	void diagnose(CropImage image, String crop) {
		LocalDateTime requestedAt = LocalDateTime.now(clock);
		long imageId = image.getImageId();
		DiagnosisResponse response;
		try {
			response = aiClient.diagnose(imageService.read(image), "image-" + imageId + "." + image.getImageFormat(), crop);
		}
		catch (IOException | RestClientException | IllegalStateException e) {
			// 파일 읽기·연결 실패·401·5xx·빈 응답. 저장 파일을 삭제하지 않고 실패로 남긴다.
			log.warn("사진 {} 진단 실패 (사진 읽기·AI 호출): {}", imageId, e.getMessage());
			save(Diagnosis.failed(imageId, image.getFarmId(), null, requestedAt));
			return;
		}

		String raw = jsonMapper.writeValueAsString(response);
		DiagnosisResult result;
		try {
			result = DiagnosisResult.from(response);
		}
		catch (IllegalStateException e) {
			// 대응표에 없는 클래스: AI 모델이 바뀐 것이므로 정상으로 뭉개지 않고 실패로 남긴다.
			log.error("사진 {} 진단 결과를 해석하지 못함: {}", imageId, e.getMessage());
			save(Diagnosis.failed(imageId, image.getFarmId(), raw, requestedAt));
			return;
		}
		Diagnosis saved = save(Diagnosis.succeeded(imageId, image.getFarmId(), result,
				jsonMapper.writeValueAsString(result.boxes()), response.modelVersion(), raw, requestedAt,
				LocalDateTime.now(clock)));
		if (saved != null) {
			afterDiagnosis(image, saved);
		}
	}

	/** 저장에 실패하면 null. */
	private Diagnosis save(Diagnosis diagnosis) {
		try {
			return diagnosisRepository.save(diagnosis);
		}
		catch (RuntimeException e) {
			// 백그라운드 스레드의 예외는 아무도 받지 않으므로 여기서 남긴다.
			log.error("사진 {} 진단 저장 실패", diagnosis.getImageId(), e);
			return null;
		}
	}

	/** 감염이면 병해 자동 대응(F-04), 정상이면 같은 카메라의 이전 정상 사진 파일을 정리한다. */
	private void afterDiagnosis(CropImage image, Diagnosis diagnosis) {
		try {
			if (diagnosis.isInfected()) {
				pestResponseService.respond(diagnosis.getFarmId(), diagnosis.getDiagnosisId(), diagnosis.getDiseaseCode());
			}
			else {
				pruneHealthyPhotos(image);
			}
		}
		catch (RuntimeException e) {
			log.error("사진 {} 진단 후처리 실패", image.getImageId(), e);
		}
	}

	/**
	 * 정상으로 진단된 사진은 카메라별 최신 1장만 파일을 남긴다. 병이 감지된 사진과 진단에 실패한 사진은 지우지 않는다.
	 * 식물마다 카메라가 있어 정상 사진까지 모두 남기면 디스크가 프로젝트 기간을 버티지 못한다(ERD 9-2).
	 */
	private void pruneHealthyPhotos(CropImage image) {
		for (CropImage previous : imageService.previousOfCamera(image)) {
			diagnosisRepository.findByImageId(previous.getImageId())
					.filter(d -> d.getDiagnosedAt() != null && !d.isInfected())
					.ifPresent(d -> imageService.deleteFile(previous));
		}
	}

	private static String aiCrop(String species) {
		try {
			return species == null ? null : DiseaseCatalog.aiCrop(species);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	public void destroy() throws InterruptedException {
		if (executor != null) {
			executor.shutdown();
			executor.awaitTermination(30, TimeUnit.SECONDS);
		}
	}
}
