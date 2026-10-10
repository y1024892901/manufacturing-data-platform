package com.mfg.crm.controller;

import com.mfg.common.exception.BizException;
import com.mfg.crm.entity.Opportunity;
import com.mfg.crm.repo.OpportunityFollowRepository;
import com.mfg.crm.repo.OpportunityRepository;
import com.mfg.crm.service.CrmRecordService;
import com.mfg.mdm.repo.CustomerRepository;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.security.scope.ScopedQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpportunityControllerStageTest {
    private final ScopedQueryService scope = mock(ScopedQueryService.class);
    private final OpportunityRepository opportunities = mock(OpportunityRepository.class);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final OpportunityController controller = new OpportunityController(
            scope, opportunities, mock(OpportunityFollowRepository.class), mock(CustomerRepository.class),
            mock(MasterDataService.class), db, mock(CrmRecordService.class));

    @Test
    void acceptsChineseStageLabelAndPersistsCanonicalStageCode() {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(17L);
        opportunity.setOpportunityNo("OPP-17");
        opportunity.setStageCode("LEAD");
        when(opportunities.findById(17L)).thenReturn(Optional.of(opportunity));
        when(opportunities.save(any(Opportunity.class))).thenAnswer(call -> call.getArgument(0));

        controller.stage(17L, Map.of("code", "需求确认"));

        assertEquals("QUALIFY", opportunity.getStageCode());
        assertNotNull(opportunity.getStageUpdatedAt());
        verifyNoInteractions(db);
    }

    @Test
    void rejectsUnknownStageWithBusinessMessage() {
        Opportunity opportunity = new Opportunity();
        opportunity.setId(17L);
        when(opportunities.findById(17L)).thenReturn(Optional.of(opportunity));

        BizException error = assertThrows(BizException.class,
                () -> controller.stage(17L, Map.of("code", "随便填")));

        assertTrue(error.getMessage().contains("请选择有效的商机阶段"));
        verify(opportunities, never()).save(any(Opportunity.class));
    }
}
