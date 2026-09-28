package com.mfg.qms.repo;
import com.mfg.qms.entity.ReworkOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface ReworkOrderRepository extends JpaRepository<ReworkOrder, Long> {
    boolean existsByReworkNo(String reworkNo);
    boolean existsByReworkNoAndIdNot(String reworkNo, Long id);
    boolean existsByDefectNo(String defectNo);
    Optional<ReworkOrder> findByReworkNo(String reworkNo);
    @Query(value="SELECT r.* FROM src_qms.qms_rework r WHERE (:keyword IS NULL OR :keyword='' OR CONCAT_WS(' ',r.rework_no,r.defect_no,r.prod_order_no,r.work_order_no,r.material_code) LIKE CONCAT('%',:keyword,'%')) AND (:status IS NULL OR :status='' OR r.rework_status=:status)",countQuery="SELECT COUNT(*) FROM src_qms.qms_rework r WHERE (:keyword IS NULL OR :keyword='' OR CONCAT_WS(' ',r.rework_no,r.defect_no,r.prod_order_no,r.work_order_no,r.material_code) LIKE CONCAT('%',:keyword,'%')) AND (:status IS NULL OR :status='' OR r.rework_status=:status)",nativeQuery=true)
    Page<ReworkOrder> search(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);
}
