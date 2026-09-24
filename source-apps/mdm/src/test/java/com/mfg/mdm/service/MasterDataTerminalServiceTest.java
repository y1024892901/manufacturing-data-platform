package com.mfg.mdm.service;

import com.mfg.common.exception.BizException;
import com.mfg.mdm.domain.MasterDataEntity;
import com.mfg.mdm.entity.*;
import com.mfg.security.service.OperationAuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MasterDataTerminalServiceTest {
    EntityManager em = mock(EntityManager.class);
    OperationAuditService audit = mock(OperationAuditService.class);
    MdmOutboxService outbox = mock(MdmOutboxService.class);
    MasterDataTerminalService service = new MasterDataTerminalService(em,audit,outbox);

    @Test void allFourKindsDisableOnceAndRejectFurtherCommands() {
        for (MasterDataEntity entity : List.of(new Material(),new Product(),new Bom(),new Routing())) {
            entity.setStatus("PUBLISHED");
            doReturn(entity).when(em).find(eq(entity.getClass()),eq(7L),eq(LockModeType.PESSIMISTIC_WRITE));
            service.disable(entity.getClass(),7L);
            assertEquals("DISABLED",entity.getStatus());
            assertFalse(entity.isPublished());
            assertThrows(BizException.class, () -> service.disable(entity.getClass(),7L));
            verify(outbox,times(1)).enqueueDisabled(entity);
        }
        verify(audit,times(4)).recordTransition(anyString(),eq("mdm"),eq("DISABLE"),anyString(),eq("7"),any(),contains("PUBLISHED"),contains("DISABLED"));
    }

    @Test void pendingApprovalCannotBeDisabled() {
        Material material = new Material(); material.setStatus("CHANGING");
        when(em.find(Material.class,7L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(material);
        assertThrows(BizException.class, () -> service.disable(Material.class,7L));
        assertEquals("CHANGING",material.getStatus());
        verifyNoInteractions(audit,outbox);
    }
}
