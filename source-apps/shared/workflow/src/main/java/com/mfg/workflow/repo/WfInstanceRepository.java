package com.mfg.workflow.repo;

import com.mfg.workflow.entity.WfInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WfInstanceRepository extends JpaRepository<WfInstance, Long> {

    Optional<WfInstance> findByInstanceNo(String instanceNo);

    /** 某业务单据当前的流程实例（用于判断"是否已在审批中"） */
    Optional<WfInstance> findFirstByBizTypeAndBizIdAndStatusOrderByIdDesc(
            String bizType, Long bizId, String status);

    /** 某业务单据的全部审批历史（按时间倒序） */
    List<WfInstance> findByBizTypeAndBizIdOrderByIdDesc(String bizType, Long bizId);

    Page<WfInstance> findBySubmitterOrderByIdDesc(String submitter, Pageable pageable);

    Page<WfInstance> findByStatusOrderByIdDesc(String status, Pageable pageable);
}
