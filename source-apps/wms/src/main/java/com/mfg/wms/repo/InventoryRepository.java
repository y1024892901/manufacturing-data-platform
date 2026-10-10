package com.mfg.wms.repo;

import com.mfg.wms.entity.Inventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByMaterialCodeAndWarehouseCodeAndLocationCodeAndBatchNo(
            String material, String warehouse, String location, String batch);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from Inventory i
            where i.materialCode = :material and i.warehouseCode = :warehouse
              and (i.locationCode = :location or (i.locationCode is null and :location is null))
              and (i.batchNo = :batch or (i.batchNo is null and :batch is null))
            """)
    Optional<Inventory> findForUpdate(@Param("material") String material,
                                      @Param("warehouse") String warehouse,
                                      @Param("location") String location,
                                      @Param("batch") String batch);
}
