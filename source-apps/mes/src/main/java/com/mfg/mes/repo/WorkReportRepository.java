package com.mfg.mes.repo;
import com.mfg.mes.entity.WorkReport;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WorkReportRepository extends JpaRepository<WorkReport, Long> { boolean existsByReportNo(String reportNo); }
