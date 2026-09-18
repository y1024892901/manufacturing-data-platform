package com.mfg.mdm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
/** BOM 表头与明细一次写入，避免无归属的用料行。 */
public record BomCommand(@NotBlank String bomCode,@NotBlank String bomName,String productCode,String bomType,String bomVersion,LocalDate effectiveDate,LocalDate expireDate,@DecimalMin("0.0001") BigDecimal baseQty,String baseUnitCode,String changeReason,String changeEcnNo,@NotEmpty List<@Valid BomLineCommand> lines){
 public record BomLineCommand(@NotNull Integer lineNo,@NotBlank String childMaterialCode,@DecimalMin("0.000001") BigDecimal qtyPer,@NotBlank String unitCode,BigDecimal scrapRate,Integer seqNo,Boolean keyMaterial,String positionDesc,String substituteGroup,String remark){}
}
