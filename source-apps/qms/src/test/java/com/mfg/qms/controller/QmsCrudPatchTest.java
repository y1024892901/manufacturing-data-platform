package com.mfg.qms.controller;

import com.mfg.qms.entity.DefectRecord;
import com.mfg.qms.entity.Inspection;
import com.mfg.qms.entity.ReworkOrder;
import com.mfg.qms.repo.DefectRecordRepository;
import com.mfg.qms.repo.InspectionRepository;
import com.mfg.qms.repo.ReworkOrderRepository;
import com.mfg.qms.service.QmsLifecycleService;
import com.mfg.security.scope.ScopedQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QmsCrudPatchTest {
    @Mock private ScopedQueryService scope;
    @Mock private InspectionRepository inspections;
    @Mock private DefectRecordRepository defects;
    @Mock private ReworkOrderRepository reworks;
    @Mock private QmsLifecycleService lifecycle;
    @Mock private JdbcTemplate db;

    @Test
    void inspectionPartialUpdateKeepsIdentifiersAndQuantitiesAndSavesOptionalContext() {
        Inspection current = new Inspection();
        current.setId(1L);
        current.setInspectionNo("INS-001");
        current.setInspectionType("IQC");
        current.setMaterialCode("MAT-001");
        current.setInspectedQty(new BigDecimal("10"));
        current.setResult("PENDING");
        when(inspections.findById(1L)).thenReturn(Optional.of(current));
        when(inspections.save(any(Inspection.class))).thenAnswer(call -> call.getArgument(0));

        Inspection patch = new Inspection();
        patch.setInspectionPoint("来料检验台");

        Inspection saved = new InspectionController(scope, inspections, lifecycle, db).update(1L, patch).data();

        assertEquals("INS-001", saved.getInspectionNo());
        assertEquals("MAT-001", saved.getMaterialCode());
        assertEquals(new BigDecimal("10"), saved.getInspectedQty());
        assertEquals("来料检验台", saved.getInspectionPoint());
    }

    @Test
    void defectPartialUpdateKeepsSourceAndQuantityAndSavesOptionalCode() {
        DefectRecord current = new DefectRecord();
        current.setId(2L);
        current.setDefectNo("DEF-001");
        current.setInspectionNo("INS-001");
        current.setDefectQty(new BigDecimal("2"));
        when(defects.findById(2L)).thenReturn(Optional.of(current));
        when(inspections.findByInspectionNo("INS-001")).thenReturn(Optional.of(inspection("INS-001", "MAT-001", "2")));
        when(defects.save(any(DefectRecord.class))).thenAnswer(call -> call.getArgument(0));

        DefectRecord patch = new DefectRecord();
        patch.setDefectCode("DIM-001");

        DefectRecord saved = new DefectController(defects, inspections, reworks).update(2L, patch).data();

        assertEquals("DEF-001", saved.getDefectNo());
        assertEquals("INS-001", saved.getInspectionNo());
        assertEquals(new BigDecimal("2"), saved.getDefectQty());
        assertEquals("DIM-001", saved.getDefectCode());
    }

    @Test
    void reworkPartialUpdateKeepsRequiredFieldsAndSavesOptionalVerificationNote() {
        ReworkOrder current = new ReworkOrder();
        current.setId(3L);
        current.setReworkNo("RW-001");
        current.setDefectNo("DEF-001");
        current.setProdOrderNo("PO-001");
        current.setReworkQty(new BigDecimal("2"));
        current.setStatus("PENDING");
        when(reworks.findById(3L)).thenReturn(Optional.of(current));
        when(defects.findByDefectNo("DEF-001")).thenReturn(Optional.of(reworkableDefect()));
        when(reworks.save(any(ReworkOrder.class))).thenAnswer(call -> call.getArgument(0));

        ReworkOrder patch = new ReworkOrder();
        patch.setVerificationNote("尺寸复验合格");

        ReworkOrder saved = new ReworkController(reworks, defects).update(3L, patch).data();

        assertEquals("RW-001", saved.getReworkNo());
        assertEquals("DEF-001", saved.getDefectNo());
        assertEquals("PO-001", saved.getProdOrderNo());
        assertEquals(new BigDecimal("2"), saved.getReworkQty());
        assertEquals("尺寸复验合格", saved.getVerificationNote());
    }

    private Inspection inspection(String inspectionNo, String materialCode, String defectQty) {
        Inspection inspection = new Inspection();
        inspection.setInspectionNo(inspectionNo);
        inspection.setMaterialCode(materialCode);
        inspection.setDefectQty(new BigDecimal(defectQty));
        return inspection;
    }

    private DefectRecord reworkableDefect() {
        DefectRecord defect = new DefectRecord();
        defect.setDefectNo("DEF-001");
        defect.setMaterialCode("MAT-001");
        defect.setDisposition("REWORK");
        defect.setDispositionQty(new BigDecimal("2"));
        return defect;
    }
}
