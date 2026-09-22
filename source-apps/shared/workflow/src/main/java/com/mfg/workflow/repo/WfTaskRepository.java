package com.mfg.workflow.repo;

import com.mfg.workflow.entity.WfTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WfTaskRepository extends JpaRepository<WfTask, Long> {

    /** 某实例的全部任务（按节点顺序） */
    List<WfTask> findByInstanceIdOrderByNodeSeqAsc(Long instanceId);

    /** 某实例某节点的任务 */
    List<WfTask> findByInstanceIdAndNodeSeq(Long instanceId, Integer nodeSeq);

    /**
     * 我的待办：任务状态为 PENDING，且审批角色属于我持有的角色。
     *
     * <p>这是「待办 N 条」的数据来源。用 IN + 角色列表，
     * 一人多岗时（如周涛身兼工艺主管与研发经理）能同时看到两边待办。
     */
    @Query("""
            SELECT t FROM WfTask t
            WHERE t.taskStatus = 'PENDING'
              AND (t.assignedUser = :username OR (t.assignedUser IS NULL AND t.approverRole IN :roles))
            ORDER BY t.createdAt ASC
            """)
    List<WfTask> findMyPending(@Param("roles") Collection<String> roles, @Param("username") String username);

    @Query("""
            SELECT t FROM WfTask t
            WHERE t.taskStatus = 'PENDING'
              AND (t.assignedUser = :username OR (t.assignedUser IS NULL AND t.approverRole IN :roles))
            ORDER BY t.createdAt ASC
            """)
    Page<WfTask> findMyPendingPaged(@Param("roles") Collection<String> roles, @Param("username") String username, Pageable pageable);

    /** 待办数量（首页角标用，避免拉全量数据） */
    @Query("""
            SELECT COUNT(t) FROM WfTask t
            WHERE t.taskStatus = 'PENDING'
              AND (t.assignedUser = :username OR (t.assignedUser IS NULL AND t.approverRole IN :roles))
            """)
    long countMyPending(@Param("roles") Collection<String> roles, @Param("username") String username);

    /** 我已处理过的任务 */
    @Query("""
            SELECT t FROM WfTask t
            WHERE t.approverUser = :username
              AND t.taskStatus <> 'PENDING'
            ORDER BY t.finishedAt DESC
            """)
    Page<WfTask> findMyHandled(@Param("username") String username, Pageable pageable);

    /** 某节点当前的待办任务 */
    @Query("""
            SELECT t FROM WfTask t
            WHERE t.instanceId = :instanceId
              AND t.nodeSeq = :nodeSeq
              AND t.taskStatus = 'PENDING'
            """)
    List<WfTask> findPendingAtNode(@Param("instanceId") Long instanceId,
                                   @Param("nodeSeq") Integer nodeSeq);
}
