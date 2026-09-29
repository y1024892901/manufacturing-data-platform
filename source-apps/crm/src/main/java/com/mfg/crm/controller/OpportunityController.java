package com.mfg.crm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.crm.entity.Opportunity;
import com.mfg.crm.entity.OpportunityFollow;
import com.mfg.crm.repo.OpportunityFollowRepository;
import com.mfg.crm.repo.OpportunityRepository;
import com.mfg.mdm.entity.Customer;
import com.mfg.mdm.repo.CustomerRepository;
import com.mfg.mdm.service.MasterDataService;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 客户、商机、跟进是 CRM 的本职；订单只在赢单后交给 ERP。 */
@RestController
@RequestMapping("/api/crm/opportunities")
@RequiredArgsConstructor
public class OpportunityController {
    private final ScopedQueryService scope;
    private final OpportunityRepository repo;
    private final OpportunityFollowRepository follows;
    private final CustomerRepository customers;
    private final MasterDataService master;
    private final JdbcTemplate db;
    private final com.mfg.crm.service.CrmRecordService records;

    @GetMapping
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:VIEW')")
    public ApiResponse<Page<Opportunity>> page(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(scope.page(ScopedResource.OPPORTUNITY, Opportunity.class, page, size, keyword, status));
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:CREATE')")
    public ApiResponse<Opportunity> create(@RequestBody Opportunity opportunity) {
        if (opportunity.getOpportunityNo() == null || opportunity.getOpportunityNo().isBlank()) {
            opportunity.setOpportunityNo("OPP-" + System.currentTimeMillis());
        }
        if (repo.existsByOpportunityNo(opportunity.getOpportunityNo())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "商机编号已存在");
        }

        String sourceLeadNo = blankToNull(opportunity.getSourceLeadNo());
        Map<String, Object> lead = sourceLeadNo == null ? null : findOpenLead(sourceLeadNo);
        Long customerId = opportunity.getCustomerId();
        if (lead != null) {
            Long leadCustomerId = ((Number) lead.get("customer_id")).longValue();
            if (customerId != null && !customerId.equals(leadCustomerId)) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "来源线索与所选客户不一致");
            }
            customerId = leadCustomerId;
        }
        if (customerId == null && blankToNull(opportunity.getCustomerCode()) != null) {
            Customer fromCode = customers.findByCustomerCode(opportunity.getCustomerCode().trim())
                    .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "客户不存在"));
            customerId = fromCode.getId();
        }
        if (customerId == null) throw BizException.of(ErrorCode.PARAM_INVALID, "请选择客户档案或来源线索");

        scope.requireVisible(ScopedResource.CUSTOMER, customerId);
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "客户不存在"));
        master.assertConsumable(customer, "CRM 商机");
        opportunity.setCustomerId(customer.getId());
        opportunity.setCustomerCode(customer.getCustomerCode());
        opportunity.setCustomerName(customer.getCustomerName());
        if (blankToNull(opportunity.getContactName()) == null) opportunity.setContactName(customer.getContactPerson());
        opportunity.setSourceLeadNo(sourceLeadNo);
        opportunity.setStageCode(sourceLeadNo == null ? "LEAD" : "QUALIFY");
        if (opportunity.getProbability() == null) opportunity.setProbability(java.math.BigDecimal.TEN);
        opportunity.setOwnerUser(CurrentUser.usernameOrSystem());
        opportunity.setOwnerName(CurrentUser.get().getRealName());
        opportunity.setDeptCode(CurrentUser.get().getDeptCode());
        opportunity.setCreatedBy(CurrentUser.usernameOrSystem());

        Opportunity saved = repo.save(opportunity);
        if (lead != null) {
            db.update("UPDATE src_crm.crm_lead SET status='CONVERTED',converted_opportunity_no=? WHERE id=?",
                    saved.getOpportunityNo(), lead.get("id"));
        }
        return ApiResponse.ok(saved);
    }

    @PostMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:STAGE')")
    public ApiResponse<Opportunity> stage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        scope.requireVisible(ScopedResource.OPPORTUNITY, id);
        String code = String.valueOf(body.getOrDefault("code", body.getOrDefault("reason", ""))).trim().toUpperCase();
        Opportunity opportunity = repo.findById(id).orElseThrow(() -> BizException.notFound("商机", id));
        if (!java.util.Set.of("LEAD", "QUALIFY", "PROPOSAL", "NEGOTIATE", "WON", "LOST").contains(code)) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "非法商机阶段");
        }
        if ("WON".equals(code)) {
            Integer count = db.queryForObject("SELECT COUNT(*) FROM src_crm.crm_quotation WHERE opportunity_no=? AND status='EFFECTIVE'",
                    Integer.class, opportunity.getOpportunityNo());
            if (count == null || count == 0) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "赢单前必须存在生效报价");
        }
        if ("LOST".equals(code)) {
            String reason = String.valueOf(body.getOrDefault("reason", "")).trim();
            if (reason.isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, "丢单原因必填");
            db.update("UPDATE src_crm.crm_opportunity SET lost_reason=? WHERE id=?", reason, id);
        }
        opportunity.setStageCode(code);
        return ApiResponse.ok(repo.save(opportunity));
    }

    @GetMapping("/{id}/follows")
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:VIEW')")
    public ApiResponse<java.util.List<OpportunityFollow>> follows(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.OPPORTUNITY, id);
        Opportunity opportunity = repo.findById(id).orElseThrow(() -> BizException.notFound("商机", id));
        return ApiResponse.ok(follows.findByOpportunityNoOrderByFollowAtDesc(opportunity.getOpportunityNo()));
    }

    @PostMapping("/{id}/follows")
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:FOLLOW')")
    public ApiResponse<OpportunityFollow> follow(@PathVariable Long id, @RequestBody OpportunityFollow follow) {
        scope.requireVisible(ScopedResource.OPPORTUNITY, id);
        Opportunity opportunity = repo.findById(id).orElseThrow(() -> BizException.notFound("商机", id));
        if (!java.util.Set.of("CALL", "VISIT", "EMAIL", "MEETING").contains(follow.getFollowType())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "非法跟进类型");
        }
        follow.setOpportunityNo(opportunity.getOpportunityNo());
        follow.setFollowUser(CurrentUser.usernameOrSystem());
        follow.setFollowAt(java.time.LocalDateTime.now());
        return ApiResponse.ok(follows.save(follow));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:VIEW')")
    public ApiResponse<Opportunity> detail(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.OPPORTUNITY, id);
        return ApiResponse.ok(repo.findById(id).orElseThrow(() -> BizException.notFound("商机", id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:UPDATE')")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        scope.requireVisible(ScopedResource.OPPORTUNITY, id);
        return ApiResponse.ok(records.update("opportunities", id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM:OPPORTUNITY:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scope.requireVisible(ScopedResource.OPPORTUNITY, id);
        records.delete("opportunities", id);
        return ApiResponse.ok();
    }

    private Map<String, Object> findOpenLead(String leadNo) {
        var rows = db.queryForList("SELECT * FROM src_crm.crm_lead WHERE lead_no=?", leadNo);
        if (rows.isEmpty()) throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "来源线索不存在");
        Map<String, Object> lead = rows.get(0);
        if (lead.get("customer_id") == null) throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "来源线索未关联客户档案");
        if (lead.get("converted_opportunity_no") != null || !java.util.Set.of("NEW", "FOLLOWING").contains(String.valueOf(lead.get("status")))) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "来源线索已转换或当前状态不可转商机");
        }
        return lead;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
