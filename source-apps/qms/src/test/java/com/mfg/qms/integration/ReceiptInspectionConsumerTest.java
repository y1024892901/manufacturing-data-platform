package com.mfg.qms.integration;

import com.mfg.common.integration.BusinessEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReceiptInspectionConsumerTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    ReceiptInspectionConsumer consumer = new ReceiptInspectionConsumer(db);
    BusinessEvent event = new BusinessEvent(7L,"event-7","WMS.RECEIPT.PENDING_INSPECTION","wms","qms",
            "RECEIPT","7",null,Map.of("receiptNo","RCV-7","inspectionNo","IQC-7"),null);
    Map<String,Object> receipt = Map.of("receipt_no","RCV-7","inspection_no","IQC-7","material_code","M-7",
            "received_qty",new BigDecimal("10.00"),"supplier_code","S-7","warehouse_code","WH-1","asn_no","ASN-7");

    @Test void createsInspectionOnceAndPreservesJudgmentOnReplay() {
        when(db.queryForList(contains("src_wms.wms_receipt"),eq("RCV-7"))).thenReturn(List.of(receipt));
        when(db.queryForList(contains("src_qms.qms_inspection"),eq("IQC-7"))).thenReturn(List.of(),
                List.of(Map.of("source_type","WMS_RECEIPT","source_no","RCV-7","material_code","M-7",
                        "inspected_qty",BigDecimal.TEN,"inspect_result","PASSED")));
        consumer.consume(event); consumer.consume(event);
        verify(db,times(1)).update(contains("INSERT INTO src_qms"),eq("IQC-7"),eq("M-7"),isNull(),eq("S-7"),eq("ASN-7"),eq(new BigDecimal("10.00")),eq("RCV-7"));
    }

    @Test void conflictingInspectionFailsInsteadOfBeingOverwritten() {
        when(db.queryForList(contains("src_wms.wms_receipt"),eq("RCV-7"))).thenReturn(List.of(receipt));
        when(db.queryForList(contains("src_qms.qms_inspection"),eq("IQC-7"))).thenReturn(List.of(Map.of("source_type","MANUAL")));
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(event));
    }

    @Test void missingReceiptIsRetryableFailure() {
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(event));
    }
}
