package com.mfg.mes.repo;

import com.mfg.mes.entity.WorkOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    boolean existsByWorkOrderNo(String workOrderNo);
    boolean existsByWorkOrderNoAndIdNot(String workOrderNo, Long id);
    Optional<WorkOrder> findByWorkOrderNo(String workOrderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkOrder w where w.id = :id")
    Optional<WorkOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkOrder w where w.workOrderNo = :workOrderNo")
    Optional<WorkOrder> findByWorkOrderNoForUpdate(@Param("workOrderNo") String workOrderNo);
}
