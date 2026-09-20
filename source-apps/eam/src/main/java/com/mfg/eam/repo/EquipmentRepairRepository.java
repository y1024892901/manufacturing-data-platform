package com.mfg.eam.repo;
import com.mfg.eam.entity.EquipmentRepair;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EquipmentRepairRepository extends JpaRepository<EquipmentRepair, Long> { boolean existsByRepairNo(String repairNo); }
