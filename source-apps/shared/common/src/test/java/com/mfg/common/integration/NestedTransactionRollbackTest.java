package com.mfg.common.integration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Connection;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Smoke runner 与调度器均用同一个 manager 的默认 REQUIRED，不开启独立提交。 */
class NestedTransactionRollbackTest {
    @Test void innerTemplatesNeverCommitOutsideRollbackOnlyOuterTransaction() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var manager = new DataSourceTransactionManager(source);
        var outer = new TransactionTemplate(manager);
        var inner = new TransactionTemplate(manager);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED,inner.getPropagationBehavior());
        outer.executeWithoutResult(status -> {
            for (int i=0; i<3; i++) inner.executeWithoutResult(child -> assertFalse(child.isNewTransaction()));
            status.setRollbackOnly();
        });
        verify(connection,never()).commit();
        verify(connection,times(1)).rollback();
        verify(source,times(1)).getConnection();
    }
}
