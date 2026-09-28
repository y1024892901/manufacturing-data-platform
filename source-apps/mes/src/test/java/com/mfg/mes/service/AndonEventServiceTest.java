package com.mfg.mes.service;

import com.mfg.mes.entity.AndonEvent;
import com.mfg.mes.repo.AndonEventRepository;
import com.mfg.mes.repo.WorkOrderRepository;
import com.mfg.security.scope.ScopedQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AndonEventServiceTest {
    @Mock private AndonEventRepository events;
    @Mock private WorkOrderRepository workOrders;
    @Mock private ScopedQueryService scope;
    @InjectMocks private AndonEventService service;

    @Test
    void eventMovesThroughRespondResolveAndCloseStates() {
        AndonEvent event = new AndonEvent();
        event.setId(5L);
        event.setStatus("OPEN");
        when(events.findByIdForUpdate(5L)).thenReturn(Optional.of(event));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.respond(5L, "维修人员已到场");
        assertEquals("RESPONDED", event.getStatus());
        assertEquals("维修人员已到场", event.getResponseNote());

        service.resolve(5L, "更换传感器后恢复生产", 12);
        assertEquals("RESOLVED", event.getStatus());
        assertEquals(12, event.getDowntimeMinutes());

        service.close(5L);
        assertEquals("CLOSED", event.getStatus());
        verify(events, times(3)).save(event);
    }
}
