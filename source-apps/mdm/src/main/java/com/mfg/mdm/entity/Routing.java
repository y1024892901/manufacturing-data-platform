package com.mfg.mdm.entity;

import com.mfg.mdm.domain.BizType;
import com.mfg.mdm.domain.MasterDataEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="md_routing", catalog="src_mdm") @Getter @Setter @NoArgsConstructor
public class Routing implements MasterDataEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="routing_code",nullable=false,length=32) private String routingCode;
    @Column(name="routing_name",nullable=false,length=200) private String routingName;
    @Column(name="product_code",length=32) private String productCode;
    @Column(name="routing_version",nullable=false,length=16) private String routingVersion="V1.0";
    @Column(name="effective_date",nullable=false) private LocalDate effectiveDate=LocalDate.now();
    @Column(name="expire_date") private LocalDate expireDate;
    @Column(name="is_current",nullable=false) private Boolean current=false;
    @Column(name="status",nullable=false,length=16) private String status="DRAFT";
    @Column(name="version_no",nullable=false) private Integer versionNo=1;
    @Column(name="change_reason",length=300) private String changeReason;
    @Column(name="created_by",length=32) private String createdBy;
    @Column(name="created_at",insertable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_by",length=32) private String updatedBy;
    @Column(name="updated_at",insertable=false,updatable=false) private LocalDateTime updatedAt;
    @Transient private List<RoutingOperation> operations=new ArrayList<>();
    @Override public String getBusinessCode(){return routingCode;}
    @Override public String getName(){return routingName;}
    @Override public BizType bizType(){return BizType.ROUTING;}
}
