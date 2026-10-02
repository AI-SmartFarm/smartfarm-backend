package com.smartfarm.backend.farm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmRepository extends JpaRepository<Farm, String> {
}
