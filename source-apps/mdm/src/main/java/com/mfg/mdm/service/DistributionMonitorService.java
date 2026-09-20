package com.mfg.mdm.service;

import com.mfg.common.exception.BizException;
import com.mfg.mdm.domain.MasterDataEntity;
import com.mfg.mdm.repo.BomRepository;
import com.mfg.mdm.repo.CustomerRepository;
import com.mfg.mdm.repo.MaterialRepository;
import com.mfg.mdm.repo.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class DistributionMonitorService {
    private final JdbcTemplate jdbc;
    private final MasterDataDistributor distributor;
    private final MaterialRepository materials;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final BomRepository boms;

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> latest(String status, int page, int size) {
        int pageIndex = Math.max(0, page - 1);
        int limit = Math.max(1, Math.min(200, size));
        int offset = pageIndex * limit;
        List<Map<String, Object>> rows;
        Long total;
        if (status == null || status.isBlank()) {
            rows = jdbc.queryForList("SELECT * FROM src_mdm.md_distribution_log ORDER BY distributed_at DESC LIMIT ? OFFSET ?", limit, offset);
            total = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_distribution_log", Long.class);
        } else {
            rows = jdbc.queryForList("SELECT * FROM src_mdm.md_distribution_log WHERE status=? ORDER BY distributed_at DESC LIMIT ? OFFSET ?", status, limit, offset);
            total = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_distribution_log WHERE status=?", Long.class, status);
        }
        return new PageImpl<>(rows, PageRequest.of(pageIndex, limit), total == null ? 0 : total);
    }

    @Transactional
    public MasterDataDistributor.DistResult retry(Long id) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT entity_type,entity_code,entity_version,target_system FROM src_mdm.md_distribution_log WHERE id=?", id);
        String type = String.valueOf(row.get("entity_type"));
        String code = String.valueOf(row.get("entity_code"));
        int version = ((Number) row.get("entity_version")).intValue();
        String target = String.valueOf(row.get("target_system"));
        MasterDataEntity entity = resolve(type, code, version);
        MasterDataDistributor.DistResult result = distributor.distributeTo(entity, target);
        jdbc.update("UPDATE src_mdm.md_distribution_log SET distribute_mode='MANUAL' WHERE id=?", id);
        return result;
    }

    private MasterDataEntity resolve(String type, String code, int version) {
        return switch (type) {
            case "MATERIAL", "PRODUCT" -> materials.findByMaterialCode(code).orElseThrow(() -> BizException.notFound("物料", code));
            case "CUSTOMER" -> customers.findByCustomerCode(code).orElseThrow(() -> BizException.notFound("客户", code));
            case "SUPPLIER" -> suppliers.findBySupplierCode(code).orElseThrow(() -> BizException.notFound("供应商", code));
            case "BOM" -> boms.findByBomCodeOrderByBomVersionDesc(code).stream()
                    .filter(item -> item.getVersionNo() != null && item.getVersionNo() == version).findFirst()
                    .orElseThrow(() -> BizException.notFound("BOM", code));
            default -> throw BizException.conflict("当前主数据类型暂不支持手工重试: " + type);
        };
    }
}
