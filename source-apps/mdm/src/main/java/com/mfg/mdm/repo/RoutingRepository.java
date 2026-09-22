package com.mfg.mdm.repo;
import com.mfg.mdm.entity.Routing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoutingRepository extends JpaRepository<Routing,Long>{boolean existsByRoutingCodeAndRoutingVersion(String code,String version);Page<Routing> findByStatus(String status,Pageable pageable);Page<Routing> findByRoutingCodeContainingOrRoutingNameContaining(String code,String name,Pageable pageable);}
