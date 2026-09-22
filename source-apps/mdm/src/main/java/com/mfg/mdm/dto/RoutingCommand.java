package com.mfg.mdm.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record RoutingCommand(@NotBlank String routingCode,@NotBlank String routingName,@NotBlank String productCode,String routingVersion,LocalDate effectiveDate,LocalDate expireDate,String changeReason,@Valid @NotEmpty List<OperationCommand> operations){public record OperationCommand(Integer opSeq,@NotBlank String operationCode,@NotBlank String operationName,String workCenter,BigDecimal setupTimeMin,BigDecimal runTimeMin,BigDecimal waitTimeMin,String defaultEquipmentCode,String requiredSkill,Boolean keyOperation,Boolean inspectionOp,Boolean inspectionRequired,String remark){}}
