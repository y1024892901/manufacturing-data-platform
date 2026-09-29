package com.mfg.mes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产实绩（工序级汇总），按生产订单一行，是「订单进度」的唯一落点。
 *
 * <p>报工或工单计划变更后，按生产订单重算本表。计划数量按各工序计划量的最大值计，
 * 完工数量与合格数量取已有报工的最高工序，避免同一产品经过多道工序时重复累计。
 */
@Entity @Table(name = "mes_prod_result", catalog = "src_mes") @Getter @Setter @NoArgsConstructor
public class ProdResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "prod_order_no") private String prodOrderNo;
    @Column(name = "product_code") private String productCode;
    @Column(name = "total_plan_qty") private BigDecimal totalPlanQty = BigDecimal.ZERO;
    @Column(name = "total_completed_qty") private BigDecimal totalCompletedQty = BigDecimal.ZERO;
    @Column(name = "total_qualified_qty") private BigDecimal totalQualifiedQty = BigDecimal.ZERO;
    /** 完工率 0~1，由最高已报工工序的累计数量推导。 */
    @Column(name = "progress_rate") private BigDecimal progressRate;
    @Column(name = "current_op_seq") private Integer currentOpSeq;
    @Column(name = "last_report_at") private LocalDateTime lastReportAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
