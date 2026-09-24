package com.mfg.qms.integration;

import com.mfg.common.integration.BusinessEvent;
import com.mfg.common.integration.BusinessEventConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/** 收货到达后创建 IQC；收货行锁与 inspection_no 唯一键保证业务幂等。 */
@Component
@RequiredArgsConstructor
public class ReceiptInspectionConsumer implements BusinessEventConsumer {
    private final JdbcTemplate db;
    public String name() { return "qms.receipt-inspection"; }
    public String targetSystem() { return "qms"; }
    public Set<String> supports() { return Set.of("WMS.RECEIPT.PENDING_INSPECTION"); }

    public void consume(BusinessEvent event) {
        String receiptNo = event.text("receiptNo");
        String inspectionNo = event.text("inspectionNo");
        if (receiptNo == null || receiptNo.isBlank() || inspectionNo == null || inspectionNo.isBlank())
            throw new IllegalArgumentException("收货事件缺少 receiptNo/inspectionNo");
        var receipts = db.queryForList("SELECT * FROM src_wms.wms_receipt WHERE receipt_no=? FOR UPDATE", receiptNo);
        if (receipts.size() != 1) throw new IllegalArgumentException("收货单不存在：" + receiptNo);
        var receipt = receipts.get(0);
        if (!inspectionNo.equals(receipt.get("inspection_no"))) throw new IllegalArgumentException("检验单号与收货单不一致");
        var existing = db.queryForList("SELECT * FROM src_qms.qms_inspection WHERE inspection_no=?", inspectionNo);
        if (!existing.isEmpty()) {
            var inspection = existing.get(0);
            if (!"WMS_RECEIPT".equals(inspection.get("source_type")) || !receiptNo.equals(inspection.get("source_no"))
                    || !Objects.equals(receipt.get("material_code"), inspection.get("material_code"))
                    || new BigDecimal(receipt.get("received_qty").toString()).compareTo(new BigDecimal(inspection.get("inspected_qty").toString())) != 0)
                throw new IllegalArgumentException("检验单号已被其他来源或数量占用");
            return; // 历史直写或重复业务事件已生成，不能覆盖后续判定结果。
        }
        db.update("""
                INSERT INTO src_qms.qms_inspection
                (inspection_no,inspection_type,material_code,batch_no,supplier_code,delivery_no,
                 inspected_qty,inspect_result,inspect_date,source_type,source_no)
                VALUES(?,'IQC',?,?,?,?,?,'PENDING',CURDATE(),'WMS_RECEIPT',?)
                """, inspectionNo, receipt.get("material_code"), receipt.get("batch_no"),
                receipt.get("supplier_code"), receipt.get("asn_no"), receipt.get("received_qty"), receiptNo);
    }
}
