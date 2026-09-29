package com.mfg.plm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.plm.entity.EngineeringChange;
import com.mfg.plm.repo.EngineeringChangeRepository;
import com.mfg.security.config.CurrentUser;
import com.mfg.workflow.dto.StartApprovalRequest;
import com.mfg.workflow.entity.WfInstance;
import com.mfg.workflow.service.ApprovalEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ECN 生命周期。实施动作由 EngineeringChangeChainService 处理，避免绕过主数据治理。 */
@Service
@RequiredArgsConstructor
public class EngineeringChangeService {
    private final EngineeringChangeRepository repo;
    private final ApprovalEngine approvalEngine;

    @Transactional
    public EngineeringChange create(EngineeringChange e) {
        require(e.getEcnNo(), "变更通知编号");
        require(e.getEcnTitle(), "变更通知标题");
        require(e.getProductCode(), "产品编码");
        require(e.getTargetType(), "变更目标类型");
        require(e.getTargetVersion(), "目标版本");
        if (repo.existsByEcnNo(e.getEcnNo())) throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "变更通知编号已存在：" + e.getEcnNo());
        e.setStatus("DRAFT");
        e.setBomCode(blankToNull(e.getBomCode()));
        e.setChangeContent(blankToNull(e.getChangeContent()));
        e.setChangeReason(blankToNull(e.getChangeReason()));
        e.setSubmittedBy(null);
        e.setApprovedBy(null);
        e.setApprovedAt(null);
        if (e.getChangeType() == null || e.getChangeType().isBlank()) e.setChangeType("DESIGN");
        return repo.save(e);
    }

    @Transactional
    public EngineeringChange update(Long id, EngineeringChange input) {
        EngineeringChange e = load(id);
        requireDraft(e);
        require(input.getEcnTitle(), "变更通知标题");
        require(input.getProductCode(), "产品编码");
        require(input.getTargetType(), "变更目标类型");
        require(input.getTargetVersion(), "目标版本");
        e.setEcnTitle(input.getEcnTitle().trim());
        e.setProductCode(input.getProductCode().trim());
        e.setBomCode(blankToNull(input.getBomCode()));
        e.setChangeType(blankToDefault(input.getChangeType(), "DESIGN"));
        e.setChangeContent(blankToNull(input.getChangeContent()));
        e.setChangeReason(blankToNull(input.getChangeReason()));
        e.setEffectiveDate(input.getEffectiveDate());
        e.setTargetType(input.getTargetType().trim());
        e.setTargetVersion(input.getTargetVersion().trim());
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        EngineeringChange e = load(id);
        requireDraft(e);
        repo.delete(e);
    }

    @Transactional
    public WfInstance submit(Long id) {
        EngineeringChange e = load(id);
        requireDraft(e);
        e.setStatus("REVIEWING");
        e.setSubmittedBy(CurrentUser.usernameOrSystem());
        repo.save(e);
        String payload = "{\"productCode\":\"" + e.getProductCode() + "\",\"bomCode\":\"" + (e.getBomCode() == null ? "" : e.getBomCode()) + "\"}";
        return approvalEngine.start(new StartApprovalRequest("ECN", e.getId(), e.getEcnNo(), e.getEcnNo() + " · " + e.getEcnTitle(), payload));
    }

    private EngineeringChange load(Long id) {
        return repo.findById(id).orElseThrow(() -> BizException.notFound("工程变更单", id));
    }

    private void requireDraft(EngineeringChange e) {
        if (!"DRAFT".equals(e.getStatus())) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有草稿变更通知可以修改或删除");
    }

    private void require(String value, String label) {
        if (value == null || value.isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, label + "不能为空");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }
}
