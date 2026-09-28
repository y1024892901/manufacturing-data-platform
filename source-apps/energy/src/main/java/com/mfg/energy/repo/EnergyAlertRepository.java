package com.mfg.energy.repo;

import com.mfg.energy.entity.EnergyAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnergyAlertRepository extends JpaRepository<EnergyAlert, Long> {
    boolean existsByAlertNoIgnoreCase(String alertNo);
    boolean existsByAlertNoIgnoreCaseAndIdNot(String alertNo, Long id);
    boolean existsBySourceTypeAndSourceId(String sourceType, Long sourceId);
    Optional<EnergyAlert> findFirstBySourceTypeAndSourceIdOrderByIdDesc(String sourceType, Long sourceId);
}
