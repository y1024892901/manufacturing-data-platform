package com.mfg.qms.repo;

import com.mfg.qms.entity.DefectRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DefectRecordRepository extends JpaRepository<DefectRecord, Long> {
    boolean existsByDefectNo(String no);
    boolean existsByDefectNoAndIdNot(String no, Long id);
    Optional<DefectRecord> findByDefectNo(String no);

    @Query(value = "SELECT d.* FROM src_qms.qms_defect d WHERE (:keyword IS NULL OR :keyword='' OR CONCAT_WS(' ',d.defect_no,d.inspection_no,d.material_code,d.defect_type,d.defect_desc) LIKE CONCAT('%',:keyword,'%')) AND (:status IS NULL OR :status='' OR (:status='OPEN' AND d.disposition IS NULL) OR d.disposition=:status)",
           countQuery = "SELECT COUNT(*) FROM src_qms.qms_defect d WHERE (:keyword IS NULL OR :keyword='' OR CONCAT_WS(' ',d.defect_no,d.inspection_no,d.material_code,d.defect_type,d.defect_desc) LIKE CONCAT('%',:keyword,'%')) AND (:status IS NULL OR :status='' OR (:status='OPEN' AND d.disposition IS NULL) OR d.disposition=:status)",
           nativeQuery = true)
    Page<DefectRecord> search(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);
}
