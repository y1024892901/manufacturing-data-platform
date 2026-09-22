package com.mfg.wms.dto;
import jakarta.validation.constraints.*;import java.math.BigDecimal;
public record InventoryActionCommand(@NotBlank String idempotencyKey,@NotBlank String materialCode,@NotBlank String warehouseCode,String locationCode,String batchNo,@NotNull @DecimalMin("0.0001") BigDecimal quantity,String sourceSystem,String sourceNo,String reason){}
