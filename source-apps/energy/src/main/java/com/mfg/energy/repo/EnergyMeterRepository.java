package com.mfg.energy.repo;

import com.mfg.energy.entity.EnergyMeter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnergyMeterRepository extends JpaRepository<EnergyMeter, Long> {
    boolean existsByMeterCodeIgnoreCase(String meterCode);
    boolean existsByMeterCodeIgnoreCaseAndIdNot(String meterCode, Long id);
    boolean existsByParentMeterCodeIgnoreCase(String parentMeterCode);
    Optional<EnergyMeter> findByMeterCodeIgnoreCase(String meterCode);
}
