package com.mfg.energy.repo;
import com.mfg.energy.entity.WorkshopUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
public interface WorkshopUsageRepository extends JpaRepository<WorkshopUsage, Long> { boolean existsByWorkshopCodeAndStatDateAndEnergyType(String workshopCode, LocalDate statDate, String energyType); }
