package com.mfg.eam.repo;

import com.mfg.eam.entity.EquipmentFault;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EquipmentFaultRepository extends JpaRepository<EquipmentFault, Long> {
    boolean existsByFaultNo(String no);
    boolean existsByFaultNoAndIdNot(String no, Long id);
    boolean existsByEquipmentCode(String equipmentCode);
    boolean existsByEquipmentCodeAndStatusAndIdNot(String equipmentCode, String status, Long id);
    Optional<EquipmentFault> findByFaultNo(String faultNo);

    /** Serialize repair creation for a fault so parallel requests cannot both pass the active-repair check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from EquipmentFault f where f.faultNo = :faultNo")
    Optional<EquipmentFault> findByFaultNoForUpdate(@Param("faultNo") String faultNo);
}
