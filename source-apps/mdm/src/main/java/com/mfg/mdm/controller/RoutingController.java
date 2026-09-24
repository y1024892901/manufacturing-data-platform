package com.mfg.mdm.controller;
import com.mfg.common.api.ApiResponse;import com.mfg.mdm.dto.RoutingCommand;import com.mfg.mdm.entity.Routing;import com.mfg.mdm.repo.RoutingRepository;import com.mfg.mdm.service.RoutingService;import com.mfg.workflow.entity.WfInstance;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.data.domain.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/mdm/routings") @RequiredArgsConstructor public class RoutingController{private final RoutingRepository repo;private final RoutingService service;
 @GetMapping @PreAuthorize("hasAuthority('MDM:ROUTING:VIEW')") public ApiResponse<Page<Routing>> page(@RequestParam(required=false)String keyword,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){var p=PageRequest.of(Math.max(0,page-1),Math.min(200,Math.max(1,size)));if(status!=null&&!status.isBlank())return ApiResponse.ok(repo.findByStatus(status,p));if(keyword!=null&&!keyword.isBlank())return ApiResponse.ok(repo.findByRoutingCodeContainingOrRoutingNameContaining(keyword,keyword,p));return ApiResponse.ok(repo.findAll(p));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('MDM:ROUTING:VIEW')") public ApiResponse<Routing> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
 @PostMapping @PreAuthorize("hasAuthority('MDM:ROUTING:UPDATE')") public ApiResponse<Routing> create(@Valid @RequestBody RoutingCommand c){return ApiResponse.ok(service.create(c));}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('MDM:ROUTING:UPDATE')") public ApiResponse<Routing> update(@PathVariable Long id,@Valid @RequestBody RoutingCommand c){return ApiResponse.ok(service.update(id,c));}
 @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('MDM:ROUTING:UPDATE')") public ApiResponse<WfInstance> submit(@PathVariable Long id){return ApiResponse.ok(service.submit(id));}
    private final com.mfg.mdm.service.MasterDataTerminalService terminalService;
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('MDM:ROUTING:DISABLE')")
    public ApiResponse<Routing> disable(@PathVariable Long id) {
        return ApiResponse.ok(terminalService.disable(Routing.class, id));
    }
}
