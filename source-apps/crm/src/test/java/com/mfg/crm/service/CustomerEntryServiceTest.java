package com.mfg.crm.service;

import com.mfg.common.exception.BizException;
import com.mfg.crm.dto.CustomerEntryCommand;
import com.mfg.mdm.entity.Customer;
import com.mfg.mdm.repo.CustomerRepository;
import com.mfg.mdm.service.MdmOutboxService;
import com.mfg.security.scope.ScopedQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerEntryServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final MdmOutboxService outbox = mock(MdmOutboxService.class);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final CustomerEntryService service = new CustomerEntryService(customers, mock(ScopedQueryService.class), outbox, db);

    @Test
    void createsPublishedMasterRecordAndQueuesDistribution() {
        when(customers.existsByCustomerCode(anyString())).thenReturn(false);
        when(customers.findByUnifiedSocialCodeAndIdNot(anyString(), anyLong())).thenReturn(List.of());
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(call -> {
            Customer saved = call.getArgument(0);
            saved.setId(81L);
            return saved;
        });
        when(outbox.enqueueAndGetEventId(any(Customer.class))).thenReturn("event-81");

        Map<String, Object> result = service.create(new CustomerEntryCommand(
                " crm-081 ", "示例制造有限公司", "示例制造", "91310000ABCDEF1234",
                "B", "DEALER", "装备制造", "上海", new BigDecimal("120000.00"),
                "NET30", null, "张女士", "13800000000", "sales@example.com", "上海市示例路1号"));

        Customer saved = (Customer) result.get("customer");
        assertEquals("CRM-081", saved.getCustomerCode());
        assertEquals("PUBLISHED", saved.getStatus());
        assertEquals(1, saved.getVersionNo());
        assertEquals("CRM录入，自动发布并进入分发队列", saved.getChangeReason());
        assertEquals("event-81", result.get("distributionEventId"));
        assertEquals("PENDING", result.get("distributionStatus"));
        verify(outbox).enqueueAndGetEventId(saved);
    }

    @Test
    void rejectsMissingCustomerNameWithoutWritingMasterOrOutbox() {
        assertThrows(BizException.class, () -> service.create(new CustomerEntryCommand(
                null, " ", null, null, null, null, null, null, null, null, null, null, null, null, null)));

        verify(customers, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void updatesPublishedMasterByIdAndQueuesTheNextVersion() {
        Customer existing = new Customer();
        existing.setId(81L);
        existing.setCustomerCode("CUS-081");
        existing.setCustomerName("旧客户名称");
        existing.setStatus("PUBLISHED");
        existing.setVersionNo(4);
        when(customers.findById(81L)).thenReturn(Optional.of(existing));
        when(customers.findByUnifiedSocialCodeAndIdNot("91310000ABCDEF1234", 81L)).thenReturn(List.of());
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
        when(outbox.enqueueAndGetEventId(any(Customer.class))).thenReturn("event-82");

        Map<String, Object> result = service.update(81L, new CustomerEntryCommand(
                "IGNORED-CLIENT-CODE", "新客户名称", "新简称", "91310000ABCDEF1234",
                "A", "DIRECT", "装备制造", "上海", new BigDecimal("90000.00"),
                "NET60", null, "李先生", "13900000000", "li@example.com", "上海市新地址"));

        Customer saved = (Customer) result.get("customer");
        assertEquals(81L, saved.getId());
        assertEquals("CUS-081", saved.getCustomerCode(), "the stable customer code is never taken from the edit form");
        assertEquals("新客户名称", saved.getCustomerName());
        assertEquals("PUBLISHED", saved.getStatus());
        assertEquals(5, saved.getVersionNo());
        assertEquals("event-82", result.get("distributionEventId"));
        assertEquals("PENDING", result.get("distributionStatus"));
        verify(db).update(contains("UPDATE src_crm.crm_lead"), any(), any(), any(), any(), any(), any(), eq(81L));
        verify(db).update(contains("UPDATE src_crm.crm_opportunity"), any(), any(), any(), eq(81L));
        verify(outbox).enqueueAndGetEventId(saved);
    }
}
