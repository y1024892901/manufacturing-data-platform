package com.mfg.mdm.repo;
import com.mfg.mdm.entity.RoutingOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RoutingOperationRepository extends JpaRepository<RoutingOperation,Long>{List<RoutingOperation> findByRoutingIdOrderByOpSeqAsc(Long routingId);void deleteByRoutingId(Long routingId);}
