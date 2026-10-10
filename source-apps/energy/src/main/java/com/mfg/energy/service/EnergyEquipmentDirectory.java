package com.mfg.energy.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Read-only equipment lookup. EAM owns the equipment register; EMS only consumes it. */
@Service
@RequiredArgsConstructor
public class EnergyEquipmentDirectory {
    private final JdbcTemplate jdbc;

    public List<EquipmentOption> listUsable() {
        return jdbc.query("""
                SELECT equipment_code, equipment_name, workshop_code, equipment_status
                FROM src_eam.eam_equipment
                WHERE equipment_status IS NULL OR equipment_status <> 'SCRAPPED'
                ORDER BY equipment_code
                """, (rs, rowNum) -> new EquipmentOption(
                rs.getString("equipment_code"), rs.getString("equipment_name"),
                rs.getString("workshop_code"), rs.getString("equipment_status")));
    }

    public EquipmentOption requireUsable(String code) {
        if (code == null || code.isBlank()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请选择设备");
        }
        EquipmentOption equipment = jdbc.query("""
                SELECT equipment_code, equipment_name, workshop_code, equipment_status
                FROM src_eam.eam_equipment
                WHERE LOWER(equipment_code) = LOWER(?)
                  AND (equipment_status IS NULL OR equipment_status <> 'SCRAPPED')
                """, rs -> rs.next() ? new EquipmentOption(
                rs.getString("equipment_code"), rs.getString("equipment_name"),
                rs.getString("workshop_code"), rs.getString("equipment_status")) : null, code);
        return Optional.ofNullable(equipment).orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND,
                "设备不存在或已报废，请从 EAM 设备台账选择有效设备"));
    }

    public record EquipmentOption(String equipmentCode, String equipmentName, String workshopCode, String status) {}
}
