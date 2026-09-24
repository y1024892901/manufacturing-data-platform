package com.mfg.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BusinessEventConsumerSchedulerTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    BusinessEventConsumer handler = mock(BusinessEventConsumer.class);
    Map<String,Object> row = new HashMap<>();
    BusinessEventConsumerScheduler scheduler;

    @BeforeEach void setup() {
        when(tx.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        when(handler.supports()).thenReturn(Set.of("WMS.RECEIPT.PENDING_INSPECTION"));
        when(handler.targetSystem()).thenReturn("qms");
        when(handler.name()).thenReturn("qms.receipt-inspection");
        row.put("id",7L); row.put("event_id","event-7");
        row.put("event_type","WMS.RECEIPT.PENDING_INSPECTION");
        row.put("target_system","qms"); row.put("source_system","wms");
        row.put("consume_status","PROCESSING"); row.put("retry_count",0);
        row.put("payload","{\"receiptNo\":\"RCV-7\",\"inspectionNo\":\"IQC-7\"}");
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of("id",7L)),List.of());
        when(db.queryForList(anyString(),eq(7L))).thenAnswer(inv -> List.of(row));
        when(db.update(contains("lease_until=DATE_ADD"),anyString(),eq(60),eq(7L))).thenAnswer(inv -> {
            row.put("consumer_name",inv.getArgument(1)); return 1;
        });
        scheduler = new BusinessEventConsumerScheduler(db,new ObjectMapper(),List.of(handler),tx);
    }

    @Test void businessAndOffsetCommitTogether() {
        scheduler.drain();
        verify(handler).consume(argThat(event -> "RCV-7".equals(event.text("receiptNo"))));
        verify(db).update(contains("consume_status='CONSUMED'"),eq("qms.receipt-inspection"),eq(7L));
        verify(db).update(contains("biz_consumer_offset"),eq("qms.receipt-inspection"),eq(row.get("event_type")),eq(7L));
        verify(tx,never()).rollback(any());
    }

    @Test void wrongTargetDoesNotRunHandler() {
        row.put("target_system","erp"); scheduler.drain();
        verify(handler,never()).consume(any());
        verify(db).update(contains("consume_status='DEAD'"),contains("无消费方"),eq(7L));
    }

    @Test void invalidJsonRollsBackAndRecordsRetry() {
        row.put("payload","invalid-json"); scheduler.drain();
        verify(handler,never()).consume(any());
        verify(tx).rollback(any());
        verify(db).update(contains("retry_count=?"),eq("FAILED"),eq(1),anyString(),eq("FAILED"),eq(2),eq(7L));
    }

    @Test void businessFailureRollsBackBeforeDeadLetter() {
        row.put("retry_count",5);
        doThrow(new IllegalStateException("business failed")).when(handler).consume(any());
        scheduler.drain();
        var order = inOrder(tx,db);
        order.verify(tx).rollback(any());
        order.verify(db).update(contains("retry_count=?"),eq("DEAD"),eq(6),eq("business failed"),eq("DEAD"),eq(32),eq(7L));
        verify(db,never()).update(contains("biz_consumer_offset"),anyString(),anyString(),anyLong());
    }

    @Test void rejectsReplayOfNonFailure() {
        assertThrows(BizException.class, () -> scheduler.replay(List.of(7L)));
        verify(tx).rollback(any());
        assertThrows(BizException.class, () -> scheduler.replay(List.of()));
    }

    @Test void replayDeduplicatesIds() {
        when(db.update(contains("consume_status='PENDING'"),eq(7L))).thenReturn(1);
        assertEquals(1,scheduler.replay(List.of(7L,7L)));
        verify(db,times(1)).update(contains("consume_status='PENDING'"),eq(7L));
    }
}
