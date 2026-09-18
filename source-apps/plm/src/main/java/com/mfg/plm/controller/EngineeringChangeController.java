package com.mfg.plm.controller;
import com.mfg.common.api.ApiResponse;import com.mfg.plm.entity.EngineeringChange;import com.mfg.plm.repo.EngineeringChangeRepository;import com.mfg.plm.service.EngineeringChangeService;import lombok.RequiredArgsConstructor;import org.springframework.data.domain.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/plm/ecns") @RequiredArgsConstructor public class EngineeringChangeController{
 private final EngineeringChangeRepository repo;private final EngineeringChangeService service;
 @GetMapping public ApiResponse<Page<EngineeringChange>> page(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(repo.findAll(PageRequest.of(Math.max(page-1,0),Math.min(size,200))));}
 @GetMapping("/{id}")public ApiResponse<EngineeringChange> detail(@PathVariable Long id){return ApiResponse.ok(repo.findById(id).orElseThrow(()->new jakarta.persistence.EntityNotFoundException("ECN不存在")));}
 @PostMapping @PreAuthorize("hasAuthority('PLM:ECN:CREATE')")public ApiResponse<EngineeringChange> create(@RequestBody EngineeringChange e){return ApiResponse.ok(service.create(e));}
 @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('PLM:ECN:CREATE')")public ApiResponse<com.mfg.workflow.entity.WfInstance> submit(@PathVariable Long id){return ApiResponse.ok(service.submit(id));}
 @PostMapping("/{id}/implement") @PreAuthorize("hasAuthority('PLM:ECN:CREATE')")public ApiResponse<EngineeringChange> implement(@PathVariable Long id){return ApiResponse.ok(service.implement(id));}
}
