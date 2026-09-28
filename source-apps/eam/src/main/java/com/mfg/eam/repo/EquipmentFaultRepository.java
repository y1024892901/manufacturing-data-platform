package com.mfg.eam.repo;

import com.mfg.eam.entity.EquipmentFault;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EquipmentFaultRepository extends JpaRepository<EquipmentFault, Long> {
    boolean existsByFaultNo(String no);
    boolean existsByFaultNoAndIdNot(String no, Long id);
    boolean existsByEquipmentCode(String equipmentCode);
    boolean existsByEquipmentCodeAndStatusAndIdNot(String equipmentCode, String status, Long id);
    Optional<EquipmentFault> findByFaultNo(String faultNo);
}
