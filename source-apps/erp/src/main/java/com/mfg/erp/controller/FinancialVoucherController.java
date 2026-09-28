package com.mfg.erp.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.erp.entity.FinancialVoucher;
import com.mfg.erp.repo.FinancialVoucherRepository;
import com.mfg.security.config.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/erp/financial-vouchers")
@RequiredArgsConstructor
public class FinancialVoucherController {
    private final FinancialVoucherRepository repo;

    @GetMapping
    @PreAuthorize("hasAuthority('ERP:VOUCHER:VIEW')")
    public ApiResponse<Page<FinancialVoucher>> page(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(repo.search(keyword, status,
                PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(size, 200)))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:VOUCHER:VIEW')")
    public ApiResponse<FinancialVoucher> detail(@PathVariable Long id) {
        return ApiResponse.ok(find(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ERP:VOUCHER:CREATE')")
    public ApiResponse<FinancialVoucher> create(@RequestBody FinancialVoucher voucher) {
        validate(voucher);
        if (repo.existsByVoucherNoAndCompanyCodeAndFiscalPeriod(
                voucher.getVoucherNo(), voucher.getCompanyCode(), voucher.getFiscalPeriod())) {
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "会计期间内凭证号重复");
        }
        voucher.setVoucherDate(voucher.getVoucherDate() == null ? LocalDate.now() : voucher.getVoucherDate());
        voucher.setVoucherStatus("DRAFT");
        voucher.setPostedAt(null);
        voucher.setPostedBy(null);
        voucher.setCreatedBy(CurrentUser.usernameOrSystem());
        return ApiResponse.ok(repo.save(voucher));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:VOUCHER:UPDATE')")
    public ApiResponse<FinancialVoucher> update(@PathVariable Long id, @RequestBody FinancialVoucher input) {
        FinancialVoucher voucher = find(id);
        requireDraft(voucher);
        validate(input);
        voucher.setVoucherDate(input.getVoucherDate() == null ? voucher.getVoucherDate() : input.getVoucherDate());
        voucher.setDebitAmount(input.getDebitAmount());
        voucher.setCreditAmount(input.getCreditAmount());
        voucher.setCostCenterCode(input.getCostCenterCode());
        voucher.setSubjectCode(input.getSubjectCode());
        voucher.setSourceType(input.getSourceType());
        voucher.setSourceNo(input.getSourceNo());
        voucher.setSalesOrderNo(input.getSalesOrderNo());
        voucher.setProdOrderNo(input.getProdOrderNo());
        voucher.setSummary(input.getSummary());
        voucher.setAttachmentUrl(input.getAttachmentUrl());
        return ApiResponse.ok(repo.save(voucher));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ERP:VOUCHER:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        FinancialVoucher voucher = find(id);
        requireDraft(voucher);
        repo.delete(voucher);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('ERP:VOUCHER:POST')")
    public ApiResponse<FinancialVoucher> post(@PathVariable Long id) {
        FinancialVoucher voucher = find(id);
        requireDraft(voucher);
        validate(voucher);
        voucher.setVoucherStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setPostedBy(CurrentUser.usernameOrSystem());
        return ApiResponse.ok(repo.save(voucher));
    }

    private FinancialVoucher find(Long id) {
        return repo.findById(id).orElseThrow(() -> BizException.notFound("财务凭证", id));
    }

    private void requireDraft(FinancialVoucher voucher) {
        if (!"DRAFT".equals(voucher.getVoucherStatus())) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "已过账凭证不能修改或删除");
        }
    }

    private void validate(FinancialVoucher voucher) {
        if (isBlank(voucher.getVoucherNo()) || isBlank(voucher.getCompanyCode()) || isBlank(voucher.getFiscalPeriod())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "凭证号、公司代码和会计期间必填");
        }
        BigDecimal debit = voucher.getDebitAmount();
        BigDecimal credit = voucher.getCreditAmount();
        if (debit == null || credit == null || debit.signum() <= 0 || credit.signum() <= 0
                || debit.compareTo(credit) != 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "凭证借贷金额必须大于0且相等");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
