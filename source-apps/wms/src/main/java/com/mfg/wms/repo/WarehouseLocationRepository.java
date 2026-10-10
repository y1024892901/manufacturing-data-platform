package com.mfg.wms.repo;

import com.mfg.wms.entity.WarehouseLocation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, Long> {
    boolean existsByLocationCode(String code);
    Optional<WarehouseLocation> findByLocationCode(String code);
}
