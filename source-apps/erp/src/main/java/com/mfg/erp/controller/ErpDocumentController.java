package com.mfg.erp.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.erp.service.ErpDocumentService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/erp/documents")
@RequiredArgsConstructor
public class ErpDocumentController {
    private final ErpDocumentService documents;

    @GetMapping("/{kind}/{id}")
    @PreAuthorize("hasAnyAuthority('ERP:MRP:VIEW','ERP:PLAN_SUGGESTION:VIEW','ERP:INVOICE:VIEW','ERP:RECEIPT:VIEW','ERP:PAYABLE:VIEW','ERP:RECEIVABLE:VIEW','ERP:CREDIT:VIEW')")
    public ApiResponse<Map<String, Object>> get(@PathVariable String kind, @PathVariable long id) {
        return ApiResponse.ok(documents.get(kind, id));
    }

    @PutMapping("/{kind}/{id}")
    @PreAuthorize("hasAnyAuthority('ERP:MRP:UPDATE','ERP:PLAN_SUGGESTION:UPDATE','ERP:INVOICE:UPDATE','ERP:RECEIVABLE:UPDATE','ERP:RECEIPT:UPDATE','ERP:PAYABLE:UPDATE')")
    public ApiResponse<Map<String, Object>> update(@PathVariable String kind, @PathVariable long id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(documents.update(kind, id, body));
    }

    @DeleteMapping("/{kind}/{id}")
    @PreAuthorize("hasAnyAuthority('ERP:MRP:DELETE','ERP:PLAN_SUGGESTION:DELETE','ERP:INVOICE:DELETE','ERP:RECEIVABLE:DELETE','ERP:RECEIPT:DELETE','ERP:PAYABLE:DELETE')")
    public ApiResponse<Void> delete(@PathVariable String kind, @PathVariable long id) {
        documents.delete(kind, id);
        return ApiResponse.ok();
    }

    @PostMapping("/invoices/{id}/issue")
    @PreAuthorize("hasAuthority('ERP:INVOICE:ISSUE')")
    public ApiResponse<Map<String, Object>> issueInvoice(@PathVariable long id) {
        return ApiResponse.ok(documents.issueInvoice(id));
    }

    @PostMapping("/receipts/{id}/post")
    @PreAuthorize("hasAuthority('ERP:RECEIPT:POST')")
    public ApiResponse<Map<String, Object>> postReceipt(@PathVariable long id) {
        return ApiResponse.ok(documents.postReceipt(id));
    }
}
