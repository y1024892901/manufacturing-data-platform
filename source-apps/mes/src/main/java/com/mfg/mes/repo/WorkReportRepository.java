package com.mfg.mes.repo;
import com.mfg.mes.entity.WorkReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {
    boolean existsByReportNo(String reportNo);
    boolean existsByReportNoAndIdNot(String reportNo, Long id);
    boolean existsByWorkOrderNo(String workOrderNo);
    List<WorkReport> findAllByWorkOrderNo(String workOrderNo);
}
