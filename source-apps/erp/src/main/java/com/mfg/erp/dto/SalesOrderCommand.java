package com.mfg.erp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderCommand(
        @NotBlank String salesOrderNo,
        @NotBlank String customerCode,
        @NotNull LocalDate deliveryDate,
        String sourceOpportunityNo,
        String customerReference,
        String paymentTerms,
        String shippingTerms,
        String shipToAddress,
        String sourceContractNo,
        String sourceType,
        String factoryCode,
        String currency,
        @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
        String remark,
        @NotEmpty List<Line> lines) {
    public record Line(
            @NotNull Integer lineNo,
            @NotBlank String materialCode,
            @NotNull @DecimalMin("0.0001") BigDecimal orderQty,
            @NotNull @DecimalMin("0") BigDecimal unitPrice,
            String unitCode,
            String customerMaterialCode,
            String remark) { }
}
