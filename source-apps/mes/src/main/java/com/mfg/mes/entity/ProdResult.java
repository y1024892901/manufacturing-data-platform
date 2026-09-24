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
 * <p>此前该表有 DDL 却无实体、无 repo、无接口，报工只累加 {@code mes_work_order} 的数量，
 * 从不写本表，因此 {@code progress_rate} 恒为 NULL。补上映射后，报工侧即可回填完工率、
 * 当前工序与最近报工时间。
 */
@Entity @Table(name = "mes_prod_result", catalog = "src_mes") @Getter @Setter @NoArgsConstructor
public class ProdResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "prod_order_no") private String prodOrderNo;
    @Column(name = "product_code") private String productCode;
    @Column(name = "total_plan_qty") private BigDecimal totalPlanQty = BigDecimal.ZERO;
    @Column(name = "total_completed_qty") private BigDecimal totalCompletedQty = BigDecimal.ZERO;
    @Column(name = "total_qualified_qty") private BigDecimal totalQualifiedQty = BigDecimal.ZERO;
    /** 完工率 0~1，由报工累计推导（当前无写入点，映射后由报工侧回填）。 */
    @Column(name = "progress_rate") private BigDecimal progressRate;
    @Column(name = "current_op_seq") private Integer currentOpSeq;
    @Column(name = "last_report_at") private LocalDateTime lastReportAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
