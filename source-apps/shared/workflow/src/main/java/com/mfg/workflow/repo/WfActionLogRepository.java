package com.mfg.workflow.repo;

import com.mfg.workflow.entity.WfActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WfActionLogRepository extends JpaRepository<WfActionLog, Long> {

    /** 审批时间轴：某实例的全部操作，按时间正序 */
    List<WfActionLog> findByInstanceIdOrderByOperatedAtAscIdAsc(Long instanceId);
}
