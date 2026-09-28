package com.mfg.energy.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.energy.entity.WorkshopUsage;
import com.mfg.energy.repo.WorkshopUsageRepository;
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
public class WorkshopUsageService {
    private static final Set<String> ENERGY_TYPES = Set.of("ELECTRIC", "WATER", "GAS", "STEAM", "COMPRESSED_AIR", "OTHER");
    private static final Set<String> UNIT_CODES = Set.of("KWH", "M3", "TON", "GJ", "MWH", "L");

    private final WorkshopUsageRepository repository;
    private final EnergyAlertService alertService;

    public List<WorkshopUsage> list() { return repository.findAll(Sort.by(Sort.Direction.DESC, "id")); }
    public WorkshopUsage get(Long id) { return repository.findById(id).orElseThrow(() -> BizException.notFound("车间能耗记录", id)); }

    @Transactional
    public WorkshopUsage create(WorkshopUsage usage) {
        normalize(usage);
        validate(usage, null);
        calculate(usage);
        LocalDateTime now = LocalDateTime.now();
        usage.setCreatedAt(now);
        usage.setUpdatedAt(now);
        WorkshopUsage saved = repository.save(usage);
        alertService.createFromUsage("WORKSHOP", saved.getId(), saved.getWorkshopCode(), saved.getEnergyType(),
                saved.getStatDate(), saved.getEnergyValue(), saved.getBaselineValue(), saved.getWarningThresholdPercent(), saved.getDeviationRate());
        return saved;
    }

    @Transactional
    public WorkshopUsage update(Long id, WorkshopUsage input) {
        WorkshopUsage current = get(id);
        normalize(input);
        validate(input, id);
        current.setWorkshopCode(input.getWorkshopCode());
        current.setStatDate(input.getStatDate());
        current.setEnergyType(input.getEnergyType());
        current.setEnergyValue(input.getEnergyValue());
        current.setUnitCode(input.getUnitCode());
        current.setTotalOutput(input.getTotalOutput());
        current.setShiftCode(input.getShiftCode());
        current.setCostCenterCode(input.getCostCenterCode());
        current.setBaselineValue(input.getBaselineValue());
        current.setWarningThresholdPercent(input.getWarningThresholdPercent());
        current.setRemark(input.getRemark());
        calculate(current);
        current.setUpdatedAt(LocalDateTime.now());
        WorkshopUsage saved = repository.save(current);
        alertService.createFromUsage("WORKSHOP", saved.getId(), saved.getWorkshopCode(), saved.getEnergyType(),
                saved.getStatDate(), saved.getEnergyValue(), saved.getBaselineValue(), saved.getWarningThresholdPercent(), saved.getDeviationRate());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        WorkshopUsage usage = get(id);
        if (alertService.hasSource("WORKSHOP", id)) throw BizException.badState("该记录已生成能耗告警，请先处理或删除关联告警");
        repository.delete(usage);
    }

    private void normalize(WorkshopUsage usage) {
        usage.setWorkshopCode(trim(usage.getWorkshopCode()));
        if (usage.getStatDate() == null) usage.setStatDate(LocalDate.now());
        usage.setEnergyType(upper(usage.getEnergyType(), "ELECTRIC"));
        usage.setUnitCode(upper(usage.getUnitCode(), "KWH"));
        usage.setShiftCode(trimToNull(usage.getShiftCode()));
        usage.setCostCenterCode(trimToNull(usage.getCostCenterCode()));
        usage.setRemark(trimToNull(usage.getRemark()));
    }

    private void validate(WorkshopUsage usage, Long excludedId) {
        if (blank(usage.getWorkshopCode()) || usage.getEnergyValue() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写车间编码和能耗量");
        }
        if (!ENERGY_TYPES.contains(usage.getEnergyType())) throw BizException.badState("能源类型无效");
        if (!UNIT_CODES.contains(usage.getUnitCode())) throw BizException.badState("计量单位无效");
        nonNegative(usage.getEnergyValue(), "能耗量");
        nonNegative(usage.getTotalOutput(), "车间总产出");
        nonNegative(usage.getBaselineValue(), "能耗基线");
        nonNegative(usage.getWarningThresholdPercent(), "预警偏差阈值");
        boolean duplicate = excludedId == null
                ? repository.existsByWorkshopCodeIgnoreCaseAndStatDateAndEnergyType(usage.getWorkshopCode(), usage.getStatDate(), usage.getEnergyType())
                : repository.existsByWorkshopCodeIgnoreCaseAndStatDateAndEnergyTypeAndIdNot(usage.getWorkshopCode(), usage.getStatDate(), usage.getEnergyType(), excludedId);
        if (duplicate) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "该车间日期与能源介质已有能耗记录");
    }

    private static void calculate(WorkshopUsage usage) {
        usage.setUnitConsumption(usage.getTotalOutput() != null && usage.getTotalOutput().signum() > 0
                ? usage.getEnergyValue().divide(usage.getTotalOutput(), 6, RoundingMode.HALF_UP) : null);
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
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String trimToNull(String value) { String result = trim(value); return blank(result) ? null : result; }
    private static String upper(String value, String fallback) { return (value == null || value.isBlank() ? fallback : value.trim()).toUpperCase(Locale.ROOT); }
}
