package com.mfg.energy.service;

import com.mfg.energy.entity.EnergyAlert;
import com.mfg.energy.entity.EnergyMeter;
import com.mfg.energy.entity.EquipmentUsage;
import com.mfg.energy.entity.WorkshopUsage;
import com.mfg.energy.repo.EnergyAlertRepository;
import com.mfg.energy.repo.EnergyMeterRepository;
import com.mfg.energy.repo.EquipmentUsageRepository;
import com.mfg.energy.repo.WorkshopUsageRepository;
import com.mfg.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnergyCrudServiceTest {
    @Mock EnergyMeterRepository meterRepository;
    @Mock EquipmentUsageRepository equipmentRepository;
    @Mock WorkshopUsageRepository workshopRepository;
    @Mock EnergyAlertRepository alertRepository;
    @Mock EnergyAlertService alertService;

    @InjectMocks EnergyMeterService meterService;
    @InjectMocks EquipmentUsageService equipmentService;
    @InjectMocks WorkshopUsageService workshopService;
    @InjectMocks EnergyAlertService energyAlertService;

    @Test
    void meterSupportsCreateReadUpdateDelete() {
        EnergyMeter meter = new EnergyMeter();
        meter.setMeterCode(" M-01 ");
        meter.setMeterName("一号总表");
        when(meterRepository.existsByMeterCodeIgnoreCase("M-01")).thenReturn(false);
        when(meterRepository.save(any(EnergyMeter.class))).thenAnswer(call -> {
            EnergyMeter saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(1L);
            return saved;
        });

        EnergyMeter created = meterService.create(meter);
        assertEquals("M-01", created.getMeterCode());
        assertEquals("ACTIVE", created.getStatus());
        when(meterRepository.findById(1L)).thenReturn(Optional.of(created));
        assertNotNull(meterService.get(1L));
        when(meterRepository.existsByMeterCodeIgnoreCaseAndIdNot("M-02", 1L)).thenReturn(false);
        EnergyMeter replacement = new EnergyMeter();
        replacement.setMeterCode("M-02");
        replacement.setMeterName("二号总表");
        assertEquals("M-02", meterService.update(1L, replacement).getMeterCode());
        when(equipmentRepository.existsByMeterCodeIgnoreCase("M-02")).thenReturn(false);
        when(meterRepository.existsByParentMeterCodeIgnoreCase("M-02")).thenReturn(false);
        meterService.delete(1L);
        verify(meterRepository).delete(created);
        when(meterRepository.findAll(any(Sort.class))).thenReturn(List.of(created));
        assertEquals(1, meterService.list().size());
    }

    @Test
    void equipmentUsageSupportsCreateReadUpdateDeleteAndUnitConsumption() {
        EquipmentUsage usage = new EquipmentUsage();
        usage.setEquipmentCode("EQ-01");
        usage.setStatDate(LocalDate.of(2026, 9, 1));
        usage.setEnergyValue(new BigDecimal("12"));
        usage.setOutputQty(new BigDecimal("3"));
        usage.setBaselinePeriodStartDate(LocalDate.of(2026, 8, 1));
        usage.setBaselinePeriodEndDate(LocalDate.of(2026, 8, 31));
        when(equipmentRepository.existsByEquipmentCodeIgnoreCaseAndStatDateAndStatHourAndEnergyType(
                anyString(), any(LocalDate.class), nullable(Integer.class), anyString())).thenReturn(false);
        when(equipmentRepository.save(any(EquipmentUsage.class))).thenAnswer(call -> {
            EquipmentUsage saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(2L);
            return saved;
        });

        EquipmentUsage created = equipmentService.create(usage);
        assertEquals(new BigDecimal("4.000000"), created.getUnitConsumption());
        assertEquals(LocalDate.of(2026, 8, 1), created.getBaselinePeriodStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), created.getBaselinePeriodEndDate());
        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(created));
        EquipmentUsage replacement = new EquipmentUsage();
        replacement.setEquipmentCode("EQ-01");
        replacement.setStatDate(LocalDate.of(2026, 9, 1));
        replacement.setEnergyValue(new BigDecimal("15"));
        replacement.setOutputQty(new BigDecimal("3"));
        replacement.setBaselinePeriodStartDate(LocalDate.of(2026, 7, 1));
        replacement.setBaselinePeriodEndDate(LocalDate.of(2026, 7, 31));
        when(equipmentRepository.existsByEquipmentCodeIgnoreCaseAndStatDateAndStatHourAndEnergyTypeAndIdNot(
                anyString(), any(LocalDate.class), nullable(Integer.class), anyString(), org.mockito.ArgumentMatchers.eq(2L))).thenReturn(false);
        EquipmentUsage updated = equipmentService.update(2L, replacement);
        assertEquals(new BigDecimal("5.000000"), updated.getUnitConsumption());
        assertEquals(LocalDate.of(2026, 7, 1), updated.getBaselinePeriodStartDate());
        assertEquals(LocalDate.of(2026, 7, 31), updated.getBaselinePeriodEndDate());
        when(alertService.hasSource("EQUIPMENT", 2L)).thenReturn(false);
        equipmentService.delete(2L);
        verify(equipmentRepository).delete(created);
    }

    @Test
    void workshopUsageSupportsCreateReadUpdateDeleteAndUnitConsumption() {
        WorkshopUsage usage = new WorkshopUsage();
        usage.setWorkshopCode("WS-01");
        usage.setStatDate(LocalDate.of(2026, 9, 1));
        usage.setEnergyValue(new BigDecimal("20"));
        usage.setTotalOutput(new BigDecimal("4"));
        usage.setBaselinePeriodStartDate(LocalDate.of(2026, 8, 1));
        usage.setBaselinePeriodEndDate(LocalDate.of(2026, 8, 31));
        when(workshopRepository.existsByWorkshopCodeIgnoreCaseAndStatDateAndEnergyType(anyString(), any(LocalDate.class), anyString())).thenReturn(false);
        when(workshopRepository.save(any(WorkshopUsage.class))).thenAnswer(call -> {
            WorkshopUsage saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(3L);
            return saved;
        });

        WorkshopUsage created = workshopService.create(usage);
        assertEquals(new BigDecimal("5.000000"), created.getUnitConsumption());
        assertEquals(LocalDate.of(2026, 8, 1), created.getBaselinePeriodStartDate());
        assertEquals(LocalDate.of(2026, 8, 31), created.getBaselinePeriodEndDate());
        when(workshopRepository.findById(3L)).thenReturn(Optional.of(created));
        WorkshopUsage replacement = new WorkshopUsage();
        replacement.setWorkshopCode("WS-01");
        replacement.setStatDate(LocalDate.of(2026, 9, 1));
        replacement.setEnergyValue(new BigDecimal("24"));
        replacement.setTotalOutput(new BigDecimal("4"));
        replacement.setBaselinePeriodStartDate(LocalDate.of(2026, 7, 1));
        replacement.setBaselinePeriodEndDate(LocalDate.of(2026, 7, 31));
        when(workshopRepository.existsByWorkshopCodeIgnoreCaseAndStatDateAndEnergyTypeAndIdNot(
                anyString(), any(LocalDate.class), anyString(), org.mockito.ArgumentMatchers.eq(3L))).thenReturn(false);
        WorkshopUsage updated = workshopService.update(3L, replacement);
        assertEquals(new BigDecimal("6.000000"), updated.getUnitConsumption());
        assertEquals(LocalDate.of(2026, 7, 1), updated.getBaselinePeriodStartDate());
        assertEquals(LocalDate.of(2026, 7, 31), updated.getBaselinePeriodEndDate());
        when(alertService.hasSource("WORKSHOP", 3L)).thenReturn(false);
        workshopService.delete(3L);
        verify(workshopRepository).delete(created);
    }

    @Test
    void baselinePeriodDatesMustBePairedAndOrdered() {
        EquipmentUsage usage = new EquipmentUsage();
        usage.setEquipmentCode("EQ-01");
        usage.setEnergyValue(new BigDecimal("12"));
        usage.setBaselinePeriodStartDate(LocalDate.of(2026, 9, 2));
        usage.setBaselinePeriodEndDate(LocalDate.of(2026, 9, 1));

        assertThrows(BizException.class, () -> equipmentService.create(usage));

        EquipmentUsage incompletePeriod = new EquipmentUsage();
        incompletePeriod.setEquipmentCode("EQ-02");
        incompletePeriod.setEnergyValue(new BigDecimal("12"));
        incompletePeriod.setBaselinePeriodStartDate(LocalDate.of(2026, 9, 1));
        assertThrows(BizException.class, () -> equipmentService.create(incompletePeriod));
    }

    @Test
    void alertSupportsCreateReadUpdateDelete() {
        EnergyAlert alert = new EnergyAlert();
        alert.setAlertNo("EA-001");
        alert.setObjectCode("EQ-01");
        alert.setActualValue(new BigDecimal("120"));
        alert.setThresholdValue(new BigDecimal("100"));
        when(alertRepository.existsByAlertNoIgnoreCase("EA-001")).thenReturn(false);
        when(alertRepository.save(any(EnergyAlert.class))).thenAnswer(call -> {
            EnergyAlert saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(4L);
            return saved;
        });

        EnergyAlert created = energyAlertService.create(alert);
        assertEquals("OPEN", created.getStatus());
        when(alertRepository.findById(4L)).thenReturn(Optional.of(created));
        EnergyAlert replacement = new EnergyAlert();
        replacement.setAlertNo("EA-001");
        replacement.setObjectCode("EQ-01");
        replacement.setActualValue(new BigDecimal("110"));
        replacement.setThresholdValue(new BigDecimal("100"));
        replacement.setStatus("CLOSED");
        when(alertRepository.existsByAlertNoIgnoreCaseAndIdNot("EA-001", 4L)).thenReturn(false);
        assertEquals("CLOSED", energyAlertService.update(4L, replacement).getStatus());
        energyAlertService.delete(4L);
        verify(alertRepository).delete(created);
    }

    @Test
    void automaticAlertTracksUsageChangesAndClosesWhenBackWithinThreshold() {
        EnergyAlert generated = new EnergyAlert();
        generated.setId(5L);
        generated.setStatus("OPEN");
        generated.setThresholdValue(new BigDecimal("110"));
        when(alertRepository.findFirstBySourceTypeAndSourceIdOrderByIdDesc("EQUIPMENT", 2L)).thenReturn(Optional.of(generated));

        energyAlertService.createFromUsage("EQUIPMENT", 2L, "EQ-01", "ELECTRIC",
                LocalDate.of(2026, 9, 1), new BigDecimal("120"), new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("20"));
        assertEquals(new BigDecimal("120"), generated.getActualValue());
        assertEquals("OPEN", generated.getStatus());
        assertEquals("HIGH", generated.getSeverity());

        energyAlertService.createFromUsage("EQUIPMENT", 2L, "EQ-01", "ELECTRIC",
                LocalDate.of(2026, 9, 2), new BigDecimal("105"), new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("5"));
        assertEquals("CLOSED", generated.getStatus());
        assertEquals("能耗数据更新后已回到预警范围，系统自动关闭告警。", generated.getCorrectiveAction());
    }
}
