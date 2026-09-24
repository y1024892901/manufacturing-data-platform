package com.mfg.mdm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.exception.BizException;
import com.mfg.mdm.entity.Product;
import com.mfg.mdm.repo.ProductRepository;
import com.mfg.mdm.service.ProductService;
import com.mfg.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/mdm/products") @RequiredArgsConstructor
public class ProductController {
    private final ProductRepository repo; private final ProductService service;
    @GetMapping @PreAuthorize("hasAuthority('MDM:PRODUCT:VIEW')") public ApiResponse<Page<Product>> page(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){var p=PageRequest.of(Math.max(0,page-1),Math.min(200,Math.max(1,size)));if(status!=null&&!status.isBlank())return ApiResponse.ok(repo.findByStatus(status,p));if(keyword!=null&&!keyword.isBlank())return ApiResponse.ok(repo.findByProductCodeContainingOrProductNameContaining(keyword,keyword,p));return ApiResponse.ok(repo.findAll(p));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('MDM:PRODUCT:VIEW')") public ApiResponse<Product> detail(@PathVariable Long id){return ApiResponse.ok(repo.findById(id).orElseThrow(()->BizException.notFound("产品",id)));}
    @GetMapping("/consumable") @PreAuthorize("hasAuthority('MDM:PRODUCT:VIEW')") public ApiResponse<List<Product>> consumable(){return ApiResponse.ok(repo.findByStatusInOrderByProductCode(List.of("PUBLISHED","CHANGING")));}
    @PostMapping @PreAuthorize("hasAuthority('MDM:PRODUCT:CREATE')") public ApiResponse<Product> create(@RequestBody Product input){return ApiResponse.ok(service.create(input));}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('MDM:PRODUCT:UPDATE')") public ApiResponse<Product> update(@PathVariable Long id,@RequestBody Product input){return ApiResponse.ok(service.update(id,input));}
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('MDM:PRODUCT:SUBMIT')") public ApiResponse<WfInstance> submit(@PathVariable Long id){return ApiResponse.ok(service.submit(id));}
    private final com.mfg.mdm.service.MasterDataTerminalService terminalService;
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('MDM:PRODUCT:DISABLE')")
    public ApiResponse<Product> disable(@PathVariable Long id) {
        return ApiResponse.ok(terminalService.disable(Product.class, id));
    }
}
