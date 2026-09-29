package com.mfg.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P3QmsEditableFieldsTest {
    @Test
    void optionalQualityContextFieldsAreAcceptedByTheGenericUpdateWhitelist() {
        assertEditable("standards", "inspectionScope", "inspection_scope", "DRAFT");
        assertEditable("sampling-plans", "samplingFrequency", "sampling_frequency", "ACTIVE");
        assertEditable("ncrs", "riskEvaluation", "risk_evaluation", "OPEN");
        assertEditable("capas", "effectivenessEvidence", "effectiveness_evidence", "DRAFT");
        assertEditable("8d", "supplierReferenceNo", "supplier_reference_no", "OPEN");
    }

    private void assertEditable(String kind, String inputField, String column, String status) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        P3CrudService service = new P3CrudService(db, List.of());
        Map<String, Object> row = Map.of("id", 7L, "status", status);
        when(db.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            return sql.startsWith("SELECT * FROM ") ? List.of(row) : List.of();
        });
        when(db.queryForList(contains("information_schema.COLUMNS"), eq(String.class), any(Object[].class)))
                .thenReturn(List.of(column, "status"));
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);

        service.update(kind, 7L, Map.of(inputField, "外部依据补充值"));

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> parameters = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(db).update(sql.capture(), parameters.capture());
        assertTrue(sql.getValue().contains("SET " + column + "=?"));
        assertArrayEquals(new Object[]{"外部依据补充值", 7L}, parameters.getValue());
    }
}
