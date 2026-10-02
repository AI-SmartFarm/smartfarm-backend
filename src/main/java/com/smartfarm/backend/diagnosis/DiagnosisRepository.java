package com.smartfarm.backend.diagnosis;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

	/** 실패한 진단(diagnosed_at 없음)은 이력에 보여 줄 결과가 없어 뺀다. */
	List<Diagnosis> findByFarmIdAndDiagnosedAtNotNullOrderByDiagnosedAtDescDiagnosisIdDesc(String farmId, Limit limit);

	Optional<Diagnosis> findByImageId(long imageId);
}
