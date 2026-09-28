package com.mfg.erp.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.erp.service.ErpDocumentService;
import com.mfg.erp.service.ErpP2Service;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/erp")
@RequiredArgsConstructor
public class ErpP2Controller {
    private final ErpP2Service service;
    private final ErpDocumentService documents;

    private ApiResponse<Page<Map<String, Object>>> page(String table, String keyword, String status, int page, int size) {
        return ApiResponse.ok(service.list(table, keyword, status, page, size));
    }

    @PostMapping("/sales-orders/{id}/credit-check")
    @PreAuthorize("hasAuthority('ERP:CREDIT:CHECK')")
    public ApiResponse<Map<String, Object>> credit(@PathVariable long id) {
        return ApiResponse.ok(service.credit(id));
    }

    @PostMapping("/sales-orders/{id}/atp")
    @PreAuthorize("hasAuthority('ERP:ATP:CHECK')")
    public ApiResponse<List<Map<String, Object>>> atp(@PathVariable long id) {
        return ApiResponse.ok(service.atp(id));
    }

    @PostMapping("/sales-orders/{id}/atp/accept")
    @PreAuthorize("hasAuthority('ERP:ATP:COMMIT')")
    public ApiResponse<List<Map<String, Object>>> acceptAtp(@PathVariable long id,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.acceptAtp(id, body == null ? null : String.valueOf(body.getOrDefault("reason", ""))));
    }

    @PostMapping("/sales-orders/{id}/credit-exception")
    @PreAuthorize("hasAuthority('ERP:CREDIT:APPROVE')")
    public ApiResponse<?> creditException(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.submitCreditException(id, String.valueOf(body.getOrDefault("reason", ""))));
    }

    @PostMapping("/sales-orders/{id}/change")
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:CHANGE')")
    public ApiResponse<Void> change(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.change(id, body);
        return ApiResponse.ok();
    }

    @PostMapping("/sales-orders/{id}/cancel")
    @PreAuthorize("hasAuthority('ERP:SALES_ORDER:CANCEL')")
    public ApiResponse<Void> cancel(@PathVariable long id, @RequestBody Map<String, Object> body) {
        service.cancel(id, String.valueOf(body.getOrDefault("reason", "")));
        return ApiResponse.ok();
    }

    @GetMapping("/credits")
    @PreAuthorize("hasAuthority('ERP:CREDIT:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> credits(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return page("erp_customer_credit", keyword, status, page, size);
    }

    @GetMapping("/mrp/runs")
    @PreAuthorize("hasAuthority('ERP:MRP:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> runs(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return page("erp_mrp_run", keyword, status, page, size);
    }

    @PostMapping("/mrp/runs")
    @PreAuthorize("hasAuthority('ERP:MRP:CREATE')")
    public ApiResponse<Map<String, Object>> run(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(documents.createMrp(body));
    }

    @PostMapping("/mrp/runs/{id}/execute")
    @PreAuthorize("hasAuthority('ERP:MRP:RUN')")
    public ApiResponse<Map<String, Object>> execute(@PathVariable long id) {
        return ApiResponse.ok(service.executeMrp(id));
    }

    @GetMapping("/mrp/suggestions")
    @PreAuthorize("hasAuthority('ERP:PLAN_SUGGESTION:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> suggestions(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return page("erp_plan_suggestion", keyword, status, page, size);
    }

    @PostMapping("/mrp/suggestions/{id}/confirm")
    @PreAuthorize("hasAuthority('ERP:PLAN_SUGGESTION:CONFIRM')")
    public ApiResponse<Map<String, Object>> suggest(@PathVariable long id) {
        return ApiResponse.ok(service.confirmSuggestion(id));
    }

    @GetMapping("/production-orders/p2")
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> orders(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return page("erp_prod_order", keyword, status, page, size);
    }

    @GetMapping("/production-orders/{id}/kit-check")
    @PreAuthorize("hasAuthority('ERP:PROD_ORDER:VIEW')")
    public ApiResponse<Map<String, Object>> kit(@PathVariable long id) {
        return ApiResponse.ok(service.kitCheck(id));
    }

    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('ERP:RECEIVABLE:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> receivables(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return page("erp_receivable", keyword, status, page, size);
    }

    @PostMapping("/receivables")
    @PreAuthorize("hasAuthority('ERP:RECEIVABLE:CREATE')")
    public ApiResponse<Map<String, Object>> receivable(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(documents.createReceivable(body));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('ERP:INVOICE:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> invoices(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return page("erp_invoice", keyword, status, page, size);
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAuthority('ERP:INVOICE:CREATE')")
    public ApiResponse<Map<String, Object>> invoice(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(documents.createInvoice(body));
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('ERP:RECEIPT:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> receipts(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return page("erp_receipt", keyword, status, page, size);
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAuthority('ERP:FINANCE:RECEIPT')")
    public ApiResponse<Map<String, Object>> receipt(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(documents.createReceipt(body));
    }

    @PostMapping("/settlements")
    @PreAuthorize("hasAuthority('ERP:FINANCE:SETTLE')")
    public ApiResponse<Void> settle(@RequestBody Map<String, Object> body) {
        service.settle(body);
        return ApiResponse.ok();
    }

    @GetMapping("/payables")
    @PreAuthorize("hasAuthority('ERP:PAYABLE:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> payables(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return page("erp_payable", keyword, status, page, size);
    }

    @PostMapping("/payables")
    @PreAuthorize("hasAuthority('ERP:PAYABLE:CREATE')")
    public ApiResponse<Map<String, Object>> payable(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(documents.createPayable(body));
    }

    @GetMapping("/order-costs/{no}")
    @PreAuthorize("hasAuthority('ERP:FINANCE:COST_VIEW')")
    public ApiResponse<Map<String, Object>> margin(@PathVariable String no) {
        return ApiResponse.ok(service.margin(no));
    }
}
