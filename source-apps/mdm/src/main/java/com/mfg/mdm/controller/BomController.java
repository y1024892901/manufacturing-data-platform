package com.mfg.mdm.controller;
import com.mfg.common.api.ApiResponse;
import com.mfg.mdm.dto.BomCommand;
import com.mfg.mdm.entity.Bom;
import com.mfg.mdm.repo.BomRepository;
import com.mfg.mdm.service.BomService;
import com.mfg.workflow.entity.WfInstance;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.math.BigDecimal;
/** BOM 的唯一维护入口；ERP、MES 只能消费已经发布的版本。 */
@RestController @RequestMapping("/api/mdm/boms") @RequiredArgsConstructor public class BomController{
 private final BomRepository repo;private final BomService service;
 @GetMapping @PreAuthorize("hasAuthority('MDM:BOM:VIEW')") public ApiResponse<Page<Bom>> page(@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){PageRequest p=PageRequest.of(Math.max(page-1,0),Math.min(size,200));return ApiResponse.ok(status==null||status.isBlank()?repo.findAll(p):repo.findByStatus(status,p));}
 @GetMapping("/consumable") @PreAuthorize("hasAuthority('MDM:BOM:VIEW')") public ApiResponse<List<Bom>> consumable(){return ApiResponse.ok(repo.findConsumable());}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('MDM:BOM:VIEW')") public ApiResponse<Bom> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
 @PostMapping @PreAuthorize("hasAuthority('MDM:BOM:CREATE')")public ApiResponse<Bom> create(@Valid @RequestBody BomCommand c){return ApiResponse.ok(service.create(c));}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('MDM:BOM:UPDATE')")public ApiResponse<Bom> update(@PathVariable Long id,@Valid @RequestBody BomCommand c){return ApiResponse.ok(service.update(id,c));}
 @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('MDM:BOM:CREATE')")public ApiResponse<WfInstance> submit(@PathVariable Long id){return ApiResponse.ok(service.submit(id));}
 @GetMapping("/{bomId}/lines/{lineId}/substitutes") @PreAuthorize("hasAuthority('MDM:BOM:VIEW')") public ApiResponse<List<com.mfg.mdm.entity.BomSubstitute>> substitutes(@PathVariable Long bomId,@PathVariable Long lineId){return ApiResponse.ok(service.substitutes(bomId,lineId));}
 @PostMapping("/{bomId}/lines/{lineId}/substitutes") @PreAuthorize("hasAuthority('MDM:BOM:UPDATE')") public ApiResponse<com.mfg.mdm.entity.BomSubstitute> addSubstitute(@PathVariable Long bomId,@PathVariable Long lineId,@RequestBody SubstituteCommand c){return ApiResponse.ok(service.addSubstitute(bomId,lineId,c.materialCode(),c.priorityNo(),c.conversionRate()));}
 @DeleteMapping("/{bomId}/lines/{lineId}/substitutes/{id}") @PreAuthorize("hasAuthority('MDM:BOM:DELETE')") public ApiResponse<Void> disableSubstitute(@PathVariable Long bomId,@PathVariable Long lineId,@PathVariable Long id){service.disableSubstitute(bomId,lineId,id);return ApiResponse.ok();}
 public record SubstituteCommand(String materialCode,Integer priorityNo,BigDecimal conversionRate){}
    private final com.mfg.mdm.service.MasterDataTerminalService terminalService;
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('MDM:BOM:DISABLE')")
    public ApiResponse<Bom> disable(@PathVariable Long id) {
        return ApiResponse.ok(terminalService.disable(Bom.class, id));
    }
}
