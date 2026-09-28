package com.mfg.wms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name="wms_location",catalog="src_wms")
@Getter @Setter @NoArgsConstructor
public class WarehouseLocation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="location_code") private String locationCode;
    @Column(name="location_name") private String locationName;
    @Column(name="warehouse_code") private String warehouseCode;
    @Column(name="warehouse_name") private String warehouseName;
    @Column(name="location_type") private String locationType;
    @Column(name="is_available") private Boolean available=true;
    @Column(name="zone_code") private String zoneCode;
    @Column(name="aisle_code") private String aisleCode;
    @Column(name="rack_code") private String rackCode;
    @Column(name="bin_code") private String binCode;
    @Column(name="max_weight_kg") private BigDecimal maxWeightKg;
    @Column(name="note") private String note;
}
