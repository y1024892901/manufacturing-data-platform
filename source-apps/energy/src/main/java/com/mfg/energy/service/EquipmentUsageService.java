package com.mfg.energy.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.energy.entity.EquipmentUsage;
import com.mfg.energy.entity.EnergyMeter;
import com.mfg.energy.repo.EquipmentUsageRepository;
import com.mfg.energy.repo.EnergyMeterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EquipmentUsageService {
    private static final Set<String> ENERGY_TYPES = Set.of("ELECTRIC", "WATER", "GAS", "STEAM", "COMPRESSED_AIR", "OTHER");
    private static final Set<String> UNIT_CODES = Set.of("KWH", "M3", "TON", "GJ", "MWH", "L");

    private final EquipmentUsageRepository repository;
    private final EnergyMeterRepository meterRepository;
    private final EnergyAlertService alertService;

    public List<EquipmentUsage> list() { return repository.findAll(Sort.by(Sort.Direction.DESC, "id")); }
    public EquipmentUsage get(Long id) { return repository.findById(id).orElseThrow(() -> BizException.notFound("设备能耗记录", id)); }

    @Transactional
    public EquipmentUsage create(EquipmentUsage usage) {
        normalize(usage);
        validate(usage, null);
        calculate(usage);
        LocalDateTime now = LocalDateTime.now();
        usage.setCreatedAt(now);
        usage.setUpdatedAt(now);
        EquipmentUsage saved = repository.save(usage);
        alertService.createFromUsage("EQUIPMENT", saved.getId(), saved.getEquipmentCode(), saved.getEnergyType(),
                saved.getStatDate(), saved.getEnergyValue(), saved.getBaselineValue(), saved.getWarningThresholdPercent(), saved.getDeviationRate());
        return saved;
    }

    @Transactional
    public EquipmentUsage update(Long id, EquipmentUsage input) {
        EquipmentUsage current = get(id);
        normalize(input);
        validate(input, id);
        current.setEquipmentCode(input.getEquipmentCode());
        current.setMeterCode(input.getMeterCode());
        current.setStatDate(input.getStatDate());
        current.setStatHour(input.getStatHour());
        current.setEnergyType(input.getEnergyType());
        current.setEnergyValue(input.getEnergyValue());
        current.setUnitCode(input.getUnitCode());
        current.setRunHours(input.getRunHours());
        current.setOutputQty(input.getOutputQty());
        current.setBaselineValue(input.getBaselineValue());
        current.setBaselinePeriodStartDate(input.getBaselinePeriodStartDate());
        current.setBaselinePeriodEndDate(input.getBaselinePeriodEndDate());
        current.setWarningThresholdPercent(input.getWarningThresholdPercent());
        current.setShiftCode(input.getShiftCode());
        current.setProductionOrderNo(input.getProductionOrderNo());
        current.setRemark(input.getRemark());
        calculate(current);
        current.setUpdatedAt(LocalDateTime.now());
        EquipmentUsage saved = repository.save(current);
        alertService.createFromUsage("EQUIPMENT", saved.getId(), saved.getEquipmentCode(), saved.getEnergyType(),
                saved.getStatDate(), saved.getEnergyValue(), saved.getBaselineValue(), saved.getWarningThresholdPercent(), saved.getDeviationRate());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        EquipmentUsage usage = get(id);
        if (alertService.hasSource("EQUIPMENT", id)) throw BizException.badState("该记录已生成能耗告警，请先处理或删除关联告警");
        repository.delete(usage);
    }

    private void normalize(EquipmentUsage usage) {
        usage.setEquipmentCode(trim(usage.getEquipmentCode()));
        usage.setMeterCode(trimToNull(usage.getMeterCode()));
        if (usage.getStatDate() == null) usage.setStatDate(LocalDate.now());
        usage.setEnergyType(upper(usage.getEnergyType(), "ELECTRIC"));
        usage.setUnitCode(upper(usage.getUnitCode(), "KWH"));
        usage.setShiftCode(trimToNull(usage.getShiftCode()));
        usage.setProductionOrderNo(trimToNull(usage.getProductionOrderNo()));
        usage.setRemark(trimToNull(usage.getRemark()));
    }

    private void validate(EquipmentUsage usage, Long excludedId) {
        if (blank(usage.getEquipmentCode()) || usage.getEnergyValue() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写设备编码和能耗量");
        }
        if (!ENERGY_TYPES.contains(usage.getEnergyType())) throw BizException.badState("能源类型无效");
        if (!UNIT_CODES.contains(usage.getUnitCode())) throw BizException.badState("计量单位无效");
        nonNegative(usage.getEnergyValue(), "能耗量");
        nonNegative(usage.getRunHours(), "运行小时");
        nonNegative(usage.getOutputQty(), "产出数量");
        nonNegative(usage.getBaselineValue(), "能耗基线");
        nonNegative(usage.getWarningThresholdPercent(), "预警偏差阈值");
        validateBaselinePeriod(usage.getBaselinePeriodStartDate(), usage.getBaselinePeriodEndDate());
        if (usage.getStatHour() != null && (usage.getStatHour() < 0 || usage.getStatHour() > 23)) {
            throw BizException.badState("统计小时应在 0 到 23 之间");
        }
        boolean duplicate = excludedId == null
                ? repository.existsByEquipmentCodeIgnoreCaseAndStatDateAndStatHourAndEnergyType(usage.getEquipmentCode(), usage.getStatDate(), usage.getStatHour(), usage.getEnergyType())
                : repository.existsByEquipmentCodeIgnoreCaseAndStatDateAndStatHourAndEnergyTypeAndIdNot(usage.getEquipmentCode(), usage.getStatDate(), usage.getStatHour(), usage.getEnergyType(), excludedId);
        if (duplicate) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "该设备在相同日期、时段和能源类型下已有记录");
        if (usage.getMeterCode() != null) {
            EnergyMeter meter = meterRepository.findByMeterCodeIgnoreCase(usage.getMeterCode())
                    .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "仪表编码不存在"));
            if (!"ACTIVE".equals(meter.getStatus())) throw BizException.badState("仪表未启用，不能录入新能耗数据");
            if (!meter.getEnergyType().equals(usage.getEnergyType())) throw BizException.badState("仪表能源类型与能耗记录不一致");
            if (!meter.getUnitCode().equals(usage.getUnitCode())) throw BizException.badState("仪表计量单位与能耗记录不一致");
            if (meter.getEquipmentCode() != null && !meter.getEquipmentCode().equalsIgnoreCase(usage.getEquipmentCode())) {
                throw BizException.badState("仪表绑定的设备与能耗记录设备不一致");
            }
            if (usage.getWarningThresholdPercent() == null) usage.setWarningThresholdPercent(meter.getWarningThresholdPercent());
        }
    }

    private static void calculate(EquipmentUsage usage) {
        usage.setUnitConsumption(usage.getOutputQty() != null && usage.getOutputQty().signum() > 0
                ? usage.getEnergyValue().divide(usage.getOutputQty(), 6, RoundingMode.HALF_UP) : null);
        if (usage.getBaselineValue() != null && usage.getBaselineValue().signum() > 0) {
            BigDecimal deviation = usage.getEnergyValue().subtract(usage.getBaselineValue())
                    .multiply(BigDecimal.valueOf(100)).divide(usage.getBaselineValue(), 4, RoundingMode.HALF_UP);
            usage.setDeviationRate(deviation);
            usage.setAbnormal(usage.getWarningThresholdPercent() != null && deviation.compareTo(usage.getWarningThresholdPercent()) > 0);
        } else {
            usage.setDeviationRate(null);
            usage.setAbnormal(false);
        }
    }

    private static void nonNegative(BigDecimal value, String label) { if (value != null && value.signum() < 0) throw BizException.badState(label + "不能小于零"); }
    private static void validateBaselinePeriod(LocalDate startDate, LocalDate endDate) {
        if ((startDate == null) != (endDate == null)) throw BizException.badState("基线期间起始日期和结束日期需同时填写");
        if (startDate != null && startDate.isAfter(endDate)) throw BizException.badState("基线期间起始日期不能晚于结束日期");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String trimToNull(String value) { String result = trim(value); return blank(result) ? null : result; }
    private static String upper(String value, String fallback) { return (value == null || value.isBlank() ? fallback : value.trim()).toUpperCase(Locale.ROOT); }
}
