package com.mfg.energy.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.energy.entity.EnergyAlert;
import com.mfg.energy.repo.EnergyAlertRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnergyAlertService {
    private static final Set<String> SOURCES = Set.of("METER", "EQUIPMENT", "WORKSHOP");
    private static final Set<String> ENERGY_TYPES = Set.of("ELECTRIC", "WATER", "GAS", "STEAM", "COMPRESSED_AIR", "OTHER");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> STATUSES = Set.of("OPEN", "PROCESSING", "CLOSED", "IGNORED");

    private final EnergyAlertRepository repository;

    public List<EnergyAlert> list() { return repository.findAll(Sort.by(Sort.Direction.DESC, "id")); }

    public boolean hasSource(String sourceType, Long sourceId) {
        return repository.existsBySourceTypeAndSourceId(sourceType, sourceId);
    }

    public EnergyAlert get(Long id) { return repository.findById(id).orElseThrow(() -> BizException.notFound("能耗告警", id)); }

    @Transactional
    public EnergyAlert create(EnergyAlert alert) {
        normalize(alert);
        validate(alert);
        if (repository.existsByAlertNoIgnoreCase(alert.getAlertNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "告警编号已存在，请更换后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        return repository.save(alert);
    }

    @Transactional
    public EnergyAlert update(Long id, EnergyAlert input) {
        EnergyAlert current = get(id);
        normalize(input);
        validate(input);
        if (repository.existsByAlertNoIgnoreCaseAndIdNot(input.getAlertNo(), id)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "告警编号已存在，请更换后重试");
        }
        current.setAlertNo(input.getAlertNo());
        current.setSourceType(input.getSourceType());
        current.setSourceId(input.getSourceId());
        current.setObjectCode(input.getObjectCode());
        current.setEnergyType(input.getEnergyType());
        current.setStatDate(input.getStatDate());
        current.setActualValue(input.getActualValue());
        current.setThresholdValue(input.getThresholdValue());
        current.setDeviationRate(input.getDeviationRate());
        current.setSeverity(input.getSeverity());
        current.setStatus(input.getStatus());
        current.setAssignedTo(input.getAssignedTo());
        current.setDueDate(input.getDueDate());
        current.setCause(input.getCause());
        current.setCorrectiveAction(input.getCorrectiveAction());
        current.setUpdatedAt(LocalDateTime.now());
        return repository.save(current);
    }

    @Transactional
    public void delete(Long id) { repository.delete(get(id)); }

    @Transactional
    public void createFromUsage(String sourceType, Long sourceId, String objectCode, String energyType,
                                LocalDate statDate, BigDecimal actualValue, BigDecimal baselineValue,
                                BigDecimal warningThresholdPercent, BigDecimal deviationRate) {
        boolean exceedsThreshold = baselineValue != null && baselineValue.signum() > 0
                && warningThresholdPercent != null && deviationRate != null
                && deviationRate.compareTo(warningThresholdPercent) > 0;
        var previous = repository.findFirstBySourceTypeAndSourceIdOrderByIdDesc(sourceType, sourceId);
        if (previous.isPresent() && ("OPEN".equals(previous.get().getStatus()) || "PROCESSING".equals(previous.get().getStatus()))) {
            EnergyAlert alert = previous.get();
            alert.setObjectCode(objectCode);
            alert.setEnergyType(energyType);
            alert.setStatDate(statDate);
            alert.setActualValue(actualValue);
            alert.setDeviationRate(deviationRate);
            if (baselineValue != null && baselineValue.signum() > 0 && warningThresholdPercent != null) {
                alert.setThresholdValue(thresholdValue(baselineValue, warningThresholdPercent));
            }
            if (exceedsThreshold) {
                alert.setSeverity(deviationRate.compareTo(warningThresholdPercent.multiply(BigDecimal.valueOf(2))) >= 0 ? "HIGH" : "MEDIUM");
            } else {
                alert.setStatus("CLOSED");
                if (blank(alert.getCorrectiveAction())) alert.setCorrectiveAction("能耗数据更新后已回到预警范围，系统自动关闭告警。");
            }
            alert.setUpdatedAt(LocalDateTime.now());
            repository.save(alert);
            return;
        }
        if (!exceedsThreshold) return;

        EnergyAlert alert = new EnergyAlert();
        alert.setAlertNo("EA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        alert.setSourceType(sourceType);
        alert.setSourceId(sourceId);
        alert.setObjectCode(objectCode);
        alert.setEnergyType(energyType);
        alert.setStatDate(statDate);
        alert.setActualValue(actualValue);
        alert.setThresholdValue(thresholdValue(baselineValue, warningThresholdPercent));
        alert.setDeviationRate(deviationRate);
        alert.setSeverity(deviationRate.compareTo(warningThresholdPercent.multiply(BigDecimal.valueOf(2))) >= 0 ? "HIGH" : "MEDIUM");
        alert.setStatus("OPEN");
        alert.setCause("实际能耗超过基线预警阈值，请核查计量数据和设备运行情况。");
        LocalDateTime now = LocalDateTime.now();
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        repository.save(alert);
    }

    private static BigDecimal thresholdValue(BigDecimal baselineValue, BigDecimal warningThresholdPercent) {
        return baselineValue.multiply(BigDecimal.ONE.add(warningThresholdPercent
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))).setScale(4, RoundingMode.HALF_UP);
    }

    private void normalize(EnergyAlert alert) {
        alert.setAlertNo(trim(alert.getAlertNo()));
        alert.setSourceType(upper(alert.getSourceType(), "METER"));
        alert.setObjectCode(trim(alert.getObjectCode()));
        alert.setEnergyType(upper(alert.getEnergyType(), "ELECTRIC"));
        alert.setSeverity(upper(alert.getSeverity(), "MEDIUM"));
        alert.setStatus(upper(alert.getStatus(), "OPEN"));
        alert.setAssignedTo(trimToNull(alert.getAssignedTo()));
        alert.setCause(trimToNull(alert.getCause()));
        alert.setCorrectiveAction(trimToNull(alert.getCorrectiveAction()));
        if (alert.getStatDate() == null) alert.setStatDate(LocalDate.now());
    }

    private void validate(EnergyAlert alert) {
        if (blank(alert.getAlertNo()) || blank(alert.getObjectCode()) || alert.getActualValue() == null || alert.getThresholdValue() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "请填写告警编号、对象编码、实际能耗和预警阈值");
        }
        if (!SOURCES.contains(alert.getSourceType())) throw BizException.badState("告警来源类型无效");
        if (!ENERGY_TYPES.contains(alert.getEnergyType())) throw BizException.badState("能源类型无效");
        if (!SEVERITIES.contains(alert.getSeverity())) throw BizException.badState("告警等级无效");
        if (!STATUSES.contains(alert.getStatus())) throw BizException.badState("告警状态无效");
        if (alert.getActualValue().signum() < 0 || alert.getThresholdValue().signum() < 0) {
            throw BizException.badState("实际能耗和预警阈值不能小于零");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String trimToNull(String value) { String result = trim(value); return blank(result) ? null : result; }
    private static String upper(String value, String fallback) { return (value == null || value.isBlank() ? fallback : value.trim()).toUpperCase(Locale.ROOT); }
}
