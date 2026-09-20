package com.mfg.qms.repo;
import com.mfg.qms.entity.ReworkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReworkOrderRepository extends JpaRepository<ReworkOrder, Long> { boolean existsByReworkNo(String reworkNo); }
