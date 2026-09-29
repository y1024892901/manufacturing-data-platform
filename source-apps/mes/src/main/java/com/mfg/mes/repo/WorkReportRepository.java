package com.mfg.mes.repo;
import com.mfg.mes.entity.WorkReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {
    boolean existsByReportNo(String reportNo);
    boolean existsByReportNoAndIdNot(String reportNo, Long id);
    boolean existsByWorkOrderNo(String workOrderNo);
    List<WorkReport> findAllByWorkOrderNo(String workOrderNo);
    List<WorkReport> findAllByProdOrderNo(String prodOrderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WorkReport r where r.id = :id")
    Optional<WorkReport> findByIdForUpdate(@Param("id") Long id);
}
