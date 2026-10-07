package com.smartfarm.backend.image;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CropImageRepository extends JpaRepository<CropImage, Long> {

	boolean existsByFarmIdAndCameraIdAndReceivedAtAfter(String farmId, String cameraId, LocalDateTime after);

	boolean existsByFarmIdAndCameraIdIsNullAndReceivedAtAfter(String farmId, LocalDateTime after);

	/** 정리 대상은 같은 카메라의 직전 사진들뿐이라 최근 것만 본다. */
	List<CropImage> findTop20ByFarmIdAndCameraIdAndImageIdLessThanOrderByImageIdDesc(String farmId, String cameraId,
			long imageId);
}
