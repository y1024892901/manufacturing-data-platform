package com.mfg.crm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.crm.dto.CustomerEntryCommand;
import com.mfg.mdm.entity.Customer;
import com.mfg.mdm.repo.CustomerRepository;
import com.mfg.mdm.service.MdmOutboxService;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.scope.ScopedQueryService;
import com.mfg.security.scope.ScopedResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** CRM's controlled entry point for the shared MDM customer master table. */
@Service
@RequiredArgsConstructor
public class CustomerEntryService {
    private static final Pattern CUSTOMER_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,31}");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Set<String> LEVELS = Set.of("A", "B", "C", "D");
    private static final Set<String> TYPES = Set.of("DIRECT", "DEALER", "AGENT");

    private final CustomerRepository customers;
    private final ScopedQueryService scopedQuery;
    private final MdmOutboxService outbox;
    private final JdbcTemplate db;

    @Transactional(readOnly = true)
    public Page<Customer> page(String keyword, int page, int size) {
        return scopedQuery.page(ScopedResource.CUSTOMER, Customer.class, page, size,
                keyword, "PUBLISHED");
    }

    @Transactional
    public Map<String, Object> create(CustomerEntryCommand input) {
        if (input == null) throw invalid("请填写客户信息");
        String name = text(input.customerName(), "客户名称", 200, true);
        String code = normalizeCode(input.customerCode());
        if (code == null) code = generateCode();
        if (customers.existsByCustomerCode(code)) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "客户编码已存在，请更换编码");
        }

        String socialCode = text(input.unifiedSocialCode(), "统一社会信用代码", 32, false);
        if (socialCode != null && !customers.findByUnifiedSocialCodeAndIdNot(socialCode, 0L).isEmpty()) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "该统一社会信用代码已登记，请先查询现有客户");
        }
        String level = enumValue(input.customerLevel(), LEVELS, "客户等级");
        String type = enumValue(input.customerType(), TYPES, "客户类型");
        BigDecimal creditLimit = input.creditLimit() == null ? BigDecimal.ZERO : input.creditLimit();
        if (creditLimit.signum() < 0 || creditLimit.precision() > 18 || creditLimit.scale() > 2) {
            throw invalid("信用额度不能为负数，且最多保留两位小数");
        }

        Customer customer = new Customer();
        customer.setCustomerCode(code);
        customer.setCustomerName(name);
        customer.setShortName(text(input.shortName(), "客户简称", 100, false));
        customer.setUnifiedSocialCode(socialCode);
        customer.setCustomerLevel(level == null ? "C" : level);
        customer.setCustomerType(type == null ? "DIRECT" : type);
        customer.setIndustry(text(input.industry(), "所属行业", 50, false));
        customer.setRegion(text(input.region(), "所属地区", 50, false));
        customer.setCreditLimit(creditLimit);
        customer.setCreditUsed(BigDecimal.ZERO);
        customer.setPaymentTerms(text(input.paymentTerms(), "付款条件", 32, false));
        customer.setTaxNo(text(input.taxNo(), "纳税人识别号", 32, false));
        customer.setContactPerson(text(input.contactPerson(), "联系人", 50, false));
        customer.setContactPhone(text(input.contactPhone(), "联系电话", 30, false));
        String email = text(input.contactEmail(), "联系邮箱", 100, false);
        if (email != null && !EMAIL.matcher(email).matches()) throw invalid("联系邮箱格式不正确");
        customer.setContactEmail(email);
        customer.setAddress(text(input.address(), "联系地址", 300, false));

        String operator = CurrentUser.usernameOrSystem();
        customer.setStatus("PUBLISHED");
        customer.setVersionNo(1);
        customer.setChangeReason("CRM录入，自动发布并进入分发队列");
        customer.setCreatedBy(operator);
        customer.setUpdatedBy(operator);

        Customer saved = customers.saveAndFlush(customer);
        String eventId = outbox.enqueueAndGetEventId(saved);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", saved);
        result.put("distributionEventId", eventId);
        result.put("distributionStatus", "PENDING");
        return result;
    }

    @Transactional
    public Map<String, Object> update(Long id, CustomerEntryCommand input) {
        if (input == null) throw invalid("请填写客户信息");
        scopedQuery.requireVisible(ScopedResource.CUSTOMER, id);
        Customer customer = customers.findById(id)
                .orElseThrow(() -> BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "客户档案不存在"));
        if (!"PUBLISHED".equals(customer.getStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有已发布的客户档案可以在 CRM 中修改");
        }

        String name = text(input.customerName(), "客户名称", 200, true);
        String socialCode = text(input.unifiedSocialCode(), "统一社会信用代码", 32, false);
        if (socialCode != null && !customers.findByUnifiedSocialCodeAndIdNot(socialCode, id).isEmpty()) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "该统一社会信用代码已登记，请先查询现有客户");
        }
        String level = enumValue(input.customerLevel(), LEVELS, "客户等级");
        String type = enumValue(input.customerType(), TYPES, "客户类型");
        BigDecimal creditLimit = input.creditLimit() == null ? BigDecimal.ZERO : input.creditLimit();
        if (creditLimit.signum() < 0 || creditLimit.precision() > 18 || creditLimit.scale() > 2) {
            throw invalid("信用额度不能为负数，且最多保留两位小数");
        }
        String email = text(input.contactEmail(), "联系邮箱", 100, false);
        if (email != null && !EMAIL.matcher(email).matches()) throw invalid("联系邮箱格式不正确");

        // The path id is the MDM primary key. Customer code is deliberately immutable here.
        customer.setCustomerName(name);
        customer.setShortName(text(input.shortName(), "客户简称", 100, false));
        customer.setUnifiedSocialCode(socialCode);
        customer.setCustomerLevel(level == null ? "C" : level);
        customer.setCustomerType(type == null ? "DIRECT" : type);
        customer.setIndustry(text(input.industry(), "所属行业", 50, false));
        customer.setRegion(text(input.region(), "所属地区", 50, false));
        customer.setCreditLimit(creditLimit);
        customer.setPaymentTerms(text(input.paymentTerms(), "付款条件", 32, false));
        customer.setTaxNo(text(input.taxNo(), "纳税人识别号", 32, false));
        customer.setContactPerson(text(input.contactPerson(), "联系人", 50, false));
        customer.setContactPhone(text(input.contactPhone(), "联系电话", 30, false));
        customer.setContactEmail(email);
        customer.setAddress(text(input.address(), "联系地址", 300, false));
        customer.setVersionNo(customer.getVersionNo() == null ? 2 : customer.getVersionNo() + 1);
        customer.setChangeReason("CRM客户档案更新，主数据版本已更新并进入分发队列");
        customer.setUpdatedBy(CurrentUser.usernameOrSystem());

        Customer saved = customers.saveAndFlush(customer);
        // Keep active CRM pipeline snapshots readable while the MDM outbox distributes the same ID/version downstream.
        db.update("UPDATE src_crm.crm_lead SET customer_code=?,customer_name=?,contact_name=?,contact_phone=?,contact_email=?,industry=? WHERE customer_id=?",
                saved.getCustomerCode(),saved.getCustomerName(),saved.getContactPerson(),saved.getContactPhone(),saved.getContactEmail(),saved.getIndustry(),saved.getId());
        db.update("UPDATE src_crm.crm_opportunity SET customer_code=?,customer_name=?,contact_name=? WHERE customer_id=?",
                saved.getCustomerCode(),saved.getCustomerName(),saved.getContactPerson(),saved.getId());
        String eventId = outbox.enqueueAndGetEventId(saved);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", saved);
        result.put("distributionEventId", eventId);
        result.put("distributionStatus", "PENDING");
        return result;
    }

    private String normalizeCode(String value) {
        String code = text(value, "客户编码", 32, false);
        if (code == null) return null;
        code = code.toUpperCase(Locale.ROOT);
        if (!CUSTOMER_CODE.matcher(code).matches()) throw invalid("客户编码仅支持英文字母、数字、短横线和下划线");
        return code;
    }

    private String generateCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "CUS-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
            if (!customers.existsByCustomerCode(code)) return code;
        }
        throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "暂时无法生成唯一客户编码，请稍后重试");
    }

    private String text(String value, String label, int max, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw invalid(label + "不能为空");
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) throw invalid(label + "长度不能超过" + max + "个字符");
        return normalized;
    }

    private String enumValue(String value, Set<String> allowed, String label) {
        String normalized = text(value, label, 16, false);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw invalid(label + "选项无效，请重新选择");
        return normalized;
    }

    private BizException invalid(String message) {
        return BizException.of(ErrorCode.PARAM_INVALID, message);
    }
}
