package com.mfg.mdm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mdm.entity.Material;
import com.mfg.mdm.repo.MaterialRepository;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.mdm.service.MasterDataDistributor;
import com.mfg.security.config.CurrentUser;
import com.mfg.workflow.entity.WfInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料主数据接口。
 *
 * <p><b>演示主线</b>：
 * <pre>
 *   1. 创建物料 M-2099（状态 DRAFT）
 *   2. 查「可选物料」列表 → 看不到 M-2099      ← 关键对比
 *   3. 提交审批 → 工艺主管 → 生产计划员 → 发布
 *   4. 再查「可选物料」列表 → M-2099 出现了    ← 前后对比
 *   5. 查分发日志 → 已推送到 ERP/MES/WMS/QMS/SRM/EAM/PLM 7 个系统
 * </pre>
 */
@Tag(name = "10. 物料主数据", description = "物料的创建、审批、发布与分发")
@RestController
@RequestMapping("/api/mdm/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialRepository repo;
    private final MasterDataService masterDataService;
    private final MasterDataDistributor distributor;

    // ---------- 查询 ----------

    @Operation(summary = "物料分页查询")
    @GetMapping
    public ApiResponse<Page<Material>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), Math.min(size, 200));

        if (status != null && !status.isBlank()) {
            return ApiResponse.ok(repo.findByStatus(status, pr));
        }
        if (materialType != null && !materialType.isBlank()) {
            return ApiResponse.ok(repo.findByMaterialType(materialType, pr));
        }
        if (keyword != null && !keyword.isBlank()) {
            return ApiResponse.ok(repo.findByMaterialNameContainingOrMaterialCodeContaining(
                    keyword, keyword, pr));
        }
        return ApiResponse.ok(repo.findAll(pr));
    }

    /**
     * ★ 业务系统可选用的物料。
     *
     * <p>只返回 PUBLISHED / CHANGING 的记录——这就是
     * 「草稿状态的物料在 ERP 里选不到」的实现点。
     */
    @Operation(summary = "可选物料列表（仅已发布）",
            description = "业务系统建单据时用。草稿/审批中的物料不会出现——这是主数据管控的核心体现")
    @GetMapping("/consumable")
    public ApiResponse<List<Map<String, Object>>> consumable() {
        List<Map<String, Object>> list = repo.findConsumable().stream()
                .map(m -> {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("materialCode", m.getMaterialCode());
                    v.put("materialName", m.getMaterialName());
                    v.put("materialSpec", m.getMaterialSpec());
                    v.put("materialType", m.getMaterialType());
                    v.put("unitLabel", m.getUnitLabel());
                    v.put("standardPrice", m.getStandardPrice());
                    v.put("versionNo", m.getVersionNo());
                    v.put("status", m.getStatus());
                    v.put("statusLabel", m.statusEnum().getLabel());
                    return v;
                })
                .toList();
        return ApiResponse.ok(list);
    }

    @Operation(summary = "物料详情")
    @GetMapping("/{id}")
    public ApiResponse<Material> detail(@PathVariable Long id) {
        return ApiResponse.ok(repo.findById(id)
                .orElseThrow(() -> BizException.notFound("物料", id)));
    }

    /** 按编码查——业务系统引用主数据时的真实调用方式 */
    @Operation(summary = "按编码查询（业务系统引用时的校验入口）")
    @GetMapping("/by-code/{code}")
    public ApiResponse<Material> byCode(@PathVariable String code) {
        Material m = repo.findByMaterialCode(code)
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND,
                        "物料编码不存在：" + code));

        // 引用前校验：未发布的不允许被业务单据引用
        masterDataService.assertConsumable(m, "业务单据");
        return ApiResponse.ok(m);
    }

    // ---------- 维护 ----------

    @Operation(summary = "新建物料", description = "创建为草稿状态，需审批后才可被业务系统使用")
    @PostMapping
    @PreAuthorize("hasAuthority('MDM:MATERIAL:CREATE')")
    public ApiResponse<Material> create(@RequestBody Material material) {
        if (repo.existsByMaterialCode(material.getMaterialCode())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS,
                    "物料编码已存在：" + material.getMaterialCode());
        }
        Material saved = masterDataService.create(material, repo);
        return ApiResponse.ok(saved);
    }

    @Operation(summary = "修改物料", description = "仅草稿/已驳回状态可直接改；已发布的需走变更审批")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MDM:MATERIAL:UPDATE')")
    public ApiResponse<Material> update(@PathVariable Long id,
                                        @RequestBody Material input,
                                        @RequestParam(required = false) String changeReason) {
        Material existing = repo.findById(id)
                .orElseThrow(() -> BizException.notFound("物料", id));

        // 只覆盖可编辑字段，治理字段由状态机控制
        existing.setMaterialName(input.getMaterialName());
        existing.setMaterialSpec(input.getMaterialSpec());
        existing.setMaterialType(input.getMaterialType());
        existing.setCategoryId(input.getCategoryId());
        existing.setBaseUnitCode(input.getBaseUnitCode());
        existing.setPurchaseUnitCode(input.getPurchaseUnitCode());
        existing.setConversionRate(input.getConversionRate());
        existing.setSafetyStock(input.getSafetyStock());
        existing.setStandardPrice(input.getStandardPrice());
        existing.setInspectionRequired(input.getInspectionRequired());
        existing.setInspectionStandard(input.getInspectionStandard());

        return ApiResponse.ok(masterDataService.update(existing, repo, changeReason));
    }

    // ---------- 审批 ----------

    @Operation(summary = "提交审批",
            description = "提交后状态变为 PENDING，走「工艺主管 → 生产计划员」两级审批")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('MDM:MATERIAL:CREATE')")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        Material m = repo.findById(id)
                .orElseThrow(() -> BizException.notFound("物料", id));

        WfInstance inst = masterDataService.submitForApproval(m, repo);
        if (inst == null) {
            return ApiResponse.ok(Map.of("message", "该类型无需审批，已直接发布"));
        }
        return ApiResponse.ok(Map.of(
                "instanceId", inst.getId(),
                "instanceNo", inst.getInstanceNo(),
                "status", m.getStatus(),
                "message", "已提交审批，等待【工艺主管】审核"));
    }

    @Operation(summary = "手动重新分发", description = "演示补偿用：把已发布物料重新推送到全部目标系统")
    @PostMapping("/{id}/redistribute")
    @PreAuthorize("hasAuthority('MDM:DISTRIBUTE:RETRY')")
    public ApiResponse<List<MasterDataDistributor.DistResult>> redistribute(@PathVariable Long id) {
        Material m = repo.findById(id)
                .orElseThrow(() -> BizException.notFound("物料", id));
        masterDataService.assertConsumable(m, "重新分发");
        return ApiResponse.ok(distributor.distribute(m));
    }

    @Operation(summary = "按状态统计", description = "首页概览用")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("draft", repo.countByStatus("DRAFT"));
        m.put("pending", repo.countByStatus("PENDING"));
        m.put("published", repo.countByStatus("PUBLISHED"));
        m.put("rejected", repo.countByStatus("REJECTED"));
        m.put("total", repo.count());
        m.put("currentUser", CurrentUser.usernameOrSystem());
        return ApiResponse.ok(m);
    }
}
