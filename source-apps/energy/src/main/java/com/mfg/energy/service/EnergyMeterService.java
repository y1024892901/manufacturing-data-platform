package com.mfg.energy.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.energy.entity.EnergyMeter;
import com.mfg.energy.repo.EnergyMeterRepository;
import com.mfg.energy.repo.EquipmentUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EnergyMeterService {
    private static final Set<String> ENERGY_TYPES = Set.of("ELECTRIC", "WATER", "GAS", "STEAM", "COMPRESSED_AIR", "OTHER");
    private static final Set<String> UNIT_CODES = Set.of("KWH", "M3", "TON", "GJ", "MWH", "L");
    private static final Set<String> METER_KINDS = Set.of("MAIN", "SUBMETER");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "MAINTENANCE");

    private final EnergyMeterRepository repository;
    private final EquipmentUsageRepository usageRepository;

    public List<EnergyMeter> list() { return repository.findAll(Sort.by(Sort.Direction.DESC, "id")); }

    public EnergyMeter get(Long id) { return repository.findById(id).orElseThrow(() -> BizException.notFound("能源仪表", id)); }

    @Transactional
    public EnergyMeter create(EnergyMeter meter) {
        normalize(meter);
        validate(meter);
        if (repository.existsByMeterCodeIgnoreCase(meter.getMeterCode())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "仪表编码已存在，请更换后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        meter.setCreatedAt(now);
        meter.setUpdatedAt(now);
        return repository.save(meter);
    }

    @Transactional
    public EnergyMeter update(Long id, EnergyMeter input) {
        EnergyMeter current = get(id);
        normalize(input);
        validate(input);
        if (repository.existsByMeterCodeIgnoreCaseAndIdNot(input.getMeterCode(), id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "仪表编码已存在，请更换后重试");
        }
        current.setMeterCode(input.getMeterCode());
        current.setMeterName(input.getMeterName());
        current.setEnergyType(input.getEnergyType());
        current.setUnitCode(input.getUnitCode());
        current.setMeterKind(input.getMeterKind());
        current.setWorkshopCode(input.getWorkshopCode());
        current.setEquipmentCode(input.getEquipmentCode());
        current.setParentMeterCode(input.getParentMeterCode());
        current.setInstallLocation(input.getInstallLocation());
        current.setMultiplier(input.getMultiplier());
        current.setReadIntervalMinutes(input.getReadIntervalMinutes());
        current.setCalibrationDate(input.getCalibrationDate());
        current.setWarningThresholdPercent(input.getWarningThresholdPercent());
        current.setDataSource(input.getDataSource());
        current.setResponsiblePerson(input.getResponsiblePerson());
        current.setStatus(input.getStatus());
        current.setRemark(input.getRemark());
        current.setUpdatedAt(LocalDateTime.now());
        return repository.save(current);
    }

    @Transactional
    public void delete(Long id) {
        EnergyMeter meter = get(id);
        if (usageRepository.existsByMeterCodeIgnoreCase(meter.getMeterCode())) {
            throw BizException.badState("该仪表已被设备能耗记录引用，请先解除引用后再删除");
        }
        if (repository.existsByParentMeterCodeIgnoreCase(meter.getMeterCode())) {
            throw BizException.badState("该仪表仍有下级分表，请先调整计量层级后再删除");
        }
        repository.delete(meter);
    }

    private void normalize(EnergyMeter meter) {
        meter.setMeterCode(trim(meter.getMeterCode()));
        meter.setMeterName(trim(meter.getMeterName()));
        meter.setEnergyType(upper(meter.getEnergyType(), "ELECTRIC"));
        meter.setUnitCode(upper(meter.getUnitCode(), "KWH"));
        meter.setMeterKind(upper(meter.getMeterKind(), "SUBMETER"));
        meter.setWorkshopCode(trimToNull(meter.getWorkshopCode()));
        meter.setEquipmentCode(trimToNull(meter.getEquipmentCode()));
        meter.setParentMeterCode(trimToNull(meter.getParentMeterCode()));
        meter.setInstallLocation(trimToNull(meter.getInstallLocation()));
        meter.setDataSource(trimToNull(meter.getDataSource()));
        meter.setResponsiblePerson(trimToNull(meter.getResponsiblePerson()));
        meter.setRemark(trimToNull(meter.getRemark()));
        meter.setStatus(upper(meter.getStatus(), "ACTIVE"));
    }

    private void validate(EnergyMeter meter) {
        if (blank(meter.getMeterCode()) || blank(meter.getMeterName()) || blank(meter.getUnitCode())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写仪表编码、名称和计量单位");
        }
        if (!ENERGY_TYPES.contains(meter.getEnergyType())) throw BizException.badState("能源类型无效");
        if (!UNIT_CODES.contains(meter.getUnitCode())) throw BizException.badState("计量单位无效");
        if (!METER_KINDS.contains(meter.getMeterKind())) throw BizException.badState("仪表层级无效");
        if (!STATUSES.contains(meter.getStatus())) throw BizException.badState("仪表状态无效");
        nonNegative(meter.getMultiplier(), "倍率");
        nonNegative(meter.getWarningThresholdPercent(), "预警偏差阈值");
        if (meter.getMultiplier() != null && meter.getMultiplier().signum() == 0) throw BizException.badState("倍率必须大于零");
        if (meter.getReadIntervalMinutes() != null && meter.getReadIntervalMinutes() <= 0) throw BizException.badState("采集间隔必须大于零");
        if (meter.getParentMeterCode() != null) {
            if (meter.getParentMeterCode().equalsIgnoreCase(meter.getMeterCode())) throw BizException.badState("上级仪表不能指向自身");
            boolean parentExists = repository.findAll().stream().anyMatch(item -> item.getMeterCode().equalsIgnoreCase(meter.getParentMeterCode()));
            if (!parentExists) throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "上级仪表编码不存在");
        }
    }

    private static void nonNegative(BigDecimal value, String label) { if (value != null && value.signum() < 0) throw BizException.badState(label + "不能小于零"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String trimToNull(String value) { String result = trim(value); return blank(result) ? null : result; }
    private static String upper(String value, String fallback) { return (value == null || value.isBlank() ? fallback : value.trim()).toUpperCase(Locale.ROOT); }
}
