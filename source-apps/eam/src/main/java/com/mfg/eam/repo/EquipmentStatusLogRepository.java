package com.mfg.eam.repo;

import com.mfg.eam.entity.EquipmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 设备状态时段台账（DDL 索引 {@code idx_esl_equip} / {@code idx_esl_start}）。 */
public interface EquipmentStatusLogRepository extends JpaRepository<EquipmentStatusLog, Long> {
    List<EquipmentStatusLog> findByEquipmentCodeOrderByStartTimeDesc(String equipmentCode);
}
