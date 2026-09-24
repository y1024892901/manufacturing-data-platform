package com.mfg.mdm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="md_bom_substitute", catalog="src_mdm")
@Getter @Setter @NoArgsConstructor
public class BomSubstitute {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="bom_line_id",nullable=false) private Long bomLineId;
    @Column(name="substitute_material_code",nullable=false,length=32) private String substituteMaterialCode;
    @Column(name="priority_no",nullable=false) private Integer priorityNo=1;
    @Column(name="conversion_rate",nullable=false,precision=18,scale=6) private BigDecimal conversionRate=BigDecimal.ONE;
    @Column(name="status",nullable=false,length=16) private String status="ACTIVE";
    @Column(name="created_at",insertable=false,updatable=false) private LocalDateTime createdAt;
}
