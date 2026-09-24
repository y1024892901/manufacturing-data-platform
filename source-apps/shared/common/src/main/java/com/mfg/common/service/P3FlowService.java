package com.mfg.common.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.common.integration.BusinessEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/** P3演示主链。每个动作同时校验来源状态、落业务数据并发布可追踪事件。 */
@Service
@RequiredArgsConstructor
public class P3FlowService {
    private final JdbcTemplate db;
    private final BusinessEventService events;
    private final java.util.List<com.mfg.common.api.DataVisibility> visibility;

    @Transactional
    public Map<String, Object> awardRfq(long id, long quoteId) {
        Map<String, Object> rfq = one("SELECT * FROM src_srm.srm_rfq WHERE id=? FOR UPDATE", id);
        if (!java.util.Set.of("PUBLISHED", "QUOTING", "CLOSED").contains(String.valueOf(rfq.get("status"))))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有已发布或已截标的询价单可以定标");
        Map<String, Object> quote = one("SELECT * FROM src_srm.srm_supplier_quote WHERE id=? AND rfq_id=?", quoteId, id);
        String poNo = "PO" + System.currentTimeMillis();
        db.update("""
            INSERT INTO src_srm.srm_purchase_order
            (purchase_order_no,supplier_code,material_code,order_qty,unit_code,unit_price,total_amount,order_date,expected_date,order_status,purchase_user,source_requisition_no)
            VALUES(?,?,?,?,?,?,?,?,?,'CREATED','system',?)
            """, poNo, quote.get("supplier_code"), rfq.get("material_code"), rfq.get("quantity"), "PCS",
                quote.get("unit_price"), decimal(rfq.get("quantity")).multiply(decimal(quote.get("unit_price"))),
                LocalDate.now(), quote.get("delivery_date"), rfq.get("source_requisition_no"));
        db.update("UPDATE src_srm.srm_rfq SET status='AWARDED',winner_supplier_code=? WHERE id=?", quote.get("supplier_code"), id);
        db.update("UPDATE src_srm.srm_supplier_quote SET status=IF(id=?,'AWARDED','LOST') WHERE rfq_id=?", quoteId, id);
        long poId = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        events.publish("SRM.PURCHASE_ORDER.SENT", "srm", "wms", "PURCHASE_ORDER", poId,
                Map.of("purchaseOrderNo", poNo, "rfqNo", rfq.get("rfq_no")));
        return one("SELECT * FROM src_srm.srm_purchase_order WHERE id=?", poId);
    }

    @Transactional
    public Map<String, Object> arriveAsn(long id, String warehouseCode) {
        Map<String, Object> asn = one("SELECT * FROM src_srm.srm_asn WHERE id=? FOR UPDATE", id);
        if (!java.util.Set.of("SHIPPED", "DRAFT").contains(String.valueOf(asn.get("status"))))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "ASN当前状态不能到货");
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM src_wms.wms_receipt WHERE asn_no=?", Integer.class, asn.get("asn_no"));
        if (exists != null && exists > 0) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "ASN已生成收货单");
        String receiptNo = "RCV" + System.currentTimeMillis();
        String inspectionNo = "IQC" + System.currentTimeMillis();
        db.update("""
            INSERT INTO src_wms.wms_receipt(receipt_no,asn_no,purchase_order_no,supplier_code,material_code,batch_no,received_qty,warehouse_code,status,inspection_no)
            VALUES(?,?,?,?,?,?,?,?,'PENDING_INSPECTION',?)
            """, receiptNo, asn.get("asn_no"), asn.get("purchase_order_no"), asn.get("supplier_code"),
                asn.get("material_code"), asn.get("batch_no"), asn.get("ship_qty"), warehouseCode, inspectionNo);
        long receiptId = db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        db.update("UPDATE src_srm.srm_asn SET status='ARRIVED' WHERE id=?", id);
        events.publish("WMS.RECEIPT.PENDING_INSPECTION", "wms", "qms", "RECEIPT", receiptId,
                Map.of("receiptNo", receiptNo, "inspectionNo", inspectionNo));
        return one("SELECT * FROM src_wms.wms_receipt WHERE id=?", receiptId);
    }

    @Transactional
    public Map<String, Object> judgeInspection(long id, BigDecimal qualifiedQty, BigDecimal defectQty, String result) {
        String sql="SELECT * FROM src_qms.qms_inspection WHERE id=?";
        java.util.List<Object> args=new java.util.ArrayList<>();args.add(id);
        for(var policy:visibility){var f=policy.filter("src_qms.qms_inspection","");sql+=" AND ("+f.sql()+")";args.addAll(f.parameters());}
        Map<String, Object> inspection = one(sql+" FOR UPDATE", args.toArray());
        if (!"PENDING".equals(inspection.get("inspect_result")))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "检验结论已生成");
        BigDecimal inspected = decimal(inspection.get("inspected_qty"));
        if (qualifiedQty.add(defectQty).compareTo(inspected) != 0)
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "合格数与不合格数之和必须等于送检数");
        if (!java.util.Set.of("PASSED", "FAILED", "CONCESSION", "REWORK", "RETURN", "SCRAPPED").contains(result))
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "非法检验结论");
        db.update("UPDATE src_qms.qms_inspection SET qualified_qty=?,defect_qty=?,defect_rate=?,inspect_result=? WHERE id=?",
                qualifiedQty, defectQty, inspected.signum() == 0 ? BigDecimal.ZERO : defectQty.divide(inspected, 4, RoundingMode.HALF_UP), result, id);
        String quality = java.util.Set.of("PASSED", "CONCESSION").contains(result) ? "AVAILABLE" : result.equals("SCRAPPED") ? "SCRAPPED" : result.equals("RETURN") ? "RETURNED" : result.equals("REWORK") ? "REWORK" : "QUARANTINED";
        var receipts = inspection.get("source_no") == null ? java.util.List.<Map<String,Object>>of() : db.queryForList("SELECT * FROM src_wms.wms_receipt WHERE receipt_no=? FOR UPDATE", inspection.get("source_no"));
        if (!receipts.isEmpty()) {
            Map<String,Object> receipt=receipts.get(0);
            db.update("UPDATE src_wms.wms_receipt SET status=? WHERE id=?", quality, receipt.get("id"));
            if ("AVAILABLE".equals(quality)) upsertInventory(receipt, qualifiedQty);
        }
        if (defectQty.signum() > 0) {
            String ncrNo = "NCR" + System.currentTimeMillis();
            db.update("INSERT INTO src_qms.qms_ncr(ncr_no,inspection_no,source_no,material_code,supplier_code,defect_qty,severity,status) VALUES(?,?,?,?,?,?,'MAJOR','OPEN')",
                    ncrNo, inspection.get("inspection_no"), inspection.get("source_no"), inspection.get("material_code"), inspection.get("supplier_code"), defectQty);
            String issueNo = "SQ" + System.currentTimeMillis();
            BigDecimal ppm = inspected.signum() == 0 ? BigDecimal.ZERO : defectQty.multiply(BigDecimal.valueOf(1_000_000)).divide(inspected, 2, RoundingMode.HALF_UP);
            db.update("INSERT INTO src_srm.srm_supplier_quality(issue_no,supplier_code,source_no,defect_qty,ppm,problem_desc) VALUES(?,?,?,?,?,?)",
                    issueNo, inspection.get("supplier_code"), inspection.get("source_no"), defectQty, ppm, "IQC检验不合格，NCR=" + ncrNo);
        }
        events.publish("QMS.INSPECTION.JUDGED", "qms", "wms", "INSPECTION", id,
                Map.of("inspectionNo", inspection.get("inspection_no"), "result", result, "inventoryStatus", quality));
        return one("SELECT * FROM src_qms.qms_inspection WHERE id=?", id);
    }

    private void upsertInventory(Map<String, Object> receipt, BigDecimal qty) {
        db.update("""
            INSERT INTO src_wms.wms_inventory(material_code,warehouse_code,location_code,batch_no,on_hand_qty,available_qty,unit_code,quality_status,received_date)
            VALUES(?,?,NULL,?,?,?,'PCS','AVAILABLE',CURDATE())
            ON DUPLICATE KEY UPDATE on_hand_qty=on_hand_qty+VALUES(on_hand_qty),available_qty=available_qty+VALUES(available_qty),quality_status='AVAILABLE',received_date=CURDATE()
            """, receipt.get("material_code"), receipt.get("warehouse_code"), receipt.get("batch_no"), qty, qty);
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        if (rows.isEmpty()) throw BizException.notFound("业务记录", "未知");
        return rows.get(0);
    }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value)); }
}
