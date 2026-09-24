package com.mfg.mdm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.mdm.service.MdmReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mdm/reference")
@RequiredArgsConstructor
public class MdmReferenceController {
    private final MdmReferenceService service;
    @GetMapping("/{type}") @PreAuthorize("hasAuthority('MDM:BASE_DATA:VIEW')")
    public ApiResponse<List<Map<String,Object>>> list(@PathVariable String type){return ApiResponse.ok(service.list(type));}
    @PostMapping("/{type}") @PreAuthorize("hasAuthority('MDM:BASE_DATA:CREATE')")
    public ApiResponse<Map<String,Object>> create(@PathVariable String type,@RequestBody Map<String,Object> input){return ApiResponse.ok(service.create(type,input));}
    @PutMapping("/{type}/{id}") @PreAuthorize("hasAuthority('MDM:BASE_DATA:UPDATE')")
    public ApiResponse<Map<String,Object>> update(@PathVariable String type,@PathVariable Long id,@RequestBody Map<String,Object> input){return ApiResponse.ok(service.update(type,id,input));}
    @DeleteMapping("/{type}/{id}") @PreAuthorize("hasAuthority('MDM:BASE_DATA:DELETE')")
    public ApiResponse<Void> disable(@PathVariable String type,@PathVariable Long id){service.disable(type,id);return ApiResponse.ok();}
}
