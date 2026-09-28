package com.mfg.erp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductionOrderCommand(
        @NotBlank String prodOrderNo,
        String salesOrderNo,
        @NotBlank String productCode,
        @DecimalMin("0.0001") BigDecimal planQty,
        @NotBlank String unitCode,
        @NotNull LocalDate planStartDate,
        @NotNull LocalDate planFinishDate,
        String bomCode,
        String bomVersion,
        String productionVersionCode,
        String priorityLevel,
        String factoryCode,
        String workshopCode,
        String costCenterCode,
        String routingCode,
        String routingVersion,
        String plannerRemark) { }
