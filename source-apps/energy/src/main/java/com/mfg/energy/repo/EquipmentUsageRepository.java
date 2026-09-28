package com.mfg.energy.repo;

import com.mfg.energy.entity.EquipmentUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentUsageRepository extends JpaRepository<EquipmentUsage, Long> {
    boolean existsByMeterCodeIgnoreCase(String meterCode);
    boolean existsByEquipmentCodeIgnoreCaseAndStatDateAndStatHourAndEnergyType(String equipmentCode, java.time.LocalDate statDate, Integer statHour, String energyType);
    boolean existsByEquipmentCodeIgnoreCaseAndStatDateAndStatHourAndEnergyTypeAndIdNot(String equipmentCode, java.time.LocalDate statDate, Integer statHour, String energyType, Long id);
}
