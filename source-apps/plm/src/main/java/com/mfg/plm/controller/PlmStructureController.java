package com.mfg.plm.controller;

import com.mfg.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/plm/structures") @RequiredArgsConstructor
public class PlmStructureController {
    private final JdbcTemplate jdbc;

    @GetMapping("/boms")
    @PreAuthorize("hasAuthority('PLM:PRODUCT:VIEW')")
    public ApiResponse<Page<Map<String,Object>>> boms(@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){int p=Math.max(0,page-1),s=Math.min(200,Math.max(1,size));String where=keyword==null||keyword.isBlank()?"":" WHERE bom_code LIKE ? OR product_code LIKE ?";List<Map<String,Object>> rows;Long total;if(where.isBlank()){rows=jdbc.queryForList("SELECT * FROM src_plm.plm_md_bom ORDER BY synced_at DESC LIMIT ? OFFSET ?",s,p*s);total=jdbc.queryForObject("SELECT COUNT(*) FROM src_plm.plm_md_bom",Long.class);}else{String like="%"+keyword.trim()+"%";rows=jdbc.queryForList("SELECT * FROM src_plm.plm_md_bom"+where+" ORDER BY synced_at DESC LIMIT ? OFFSET ?",like,like,s,p*s);total=jdbc.queryForObject("SELECT COUNT(*) FROM src_plm.plm_md_bom"+where,Long.class,like,like);}return ApiResponse.ok(new PageImpl<>(rows,PageRequest.of(p,s),total==null?0:total));}

    @GetMapping("/boms/{code}/{version}")
    @PreAuthorize("hasAuthority('PLM:PRODUCT:VIEW')")
    public ApiResponse<Map<String,Object>> detail(@PathVariable String code,@PathVariable String version){Map<String,Object> result=new LinkedHashMap<>();result.put("header",jdbc.queryForMap("SELECT * FROM src_plm.plm_md_bom WHERE bom_code=? AND bom_version=?",code,version));result.put("lines",jdbc.queryForList("SELECT * FROM src_plm.plm_md_bom_line WHERE bom_code=? AND bom_version=? ORDER BY line_no",code,version));return ApiResponse.ok(result);}

    @GetMapping("/boms/compare")
    @PreAuthorize("hasAuthority('PLM:PRODUCT:VIEW')")
    public ApiResponse<List<Map<String,Object>>> compare(@RequestParam String code,@RequestParam String left,@RequestParam String right){List<Map<String,Object>> a=lines(code,left),b=lines(code,right);Map<String,Map<String,Object>> am=a.stream().collect(Collectors.toMap(x->String.valueOf(x.get("child_material_code")),Function.identity(),(x,y)->x,LinkedHashMap::new));Map<String,Map<String,Object>> bm=b.stream().collect(Collectors.toMap(x->String.valueOf(x.get("child_material_code")),Function.identity(),(x,y)->x,LinkedHashMap::new));Set<String> keys=new LinkedHashSet<>();keys.addAll(am.keySet());keys.addAll(bm.keySet());List<Map<String,Object>> out=new ArrayList<>();for(String material:keys){Map<String,Object> l=am.get(material),r=bm.get(material);BigDecimal lq=decimal(l),rq=decimal(r);Map<String,Object> row=new LinkedHashMap<>();row.put("materialCode",material);row.put("leftQty",lq);row.put("rightQty",rq);row.put("deltaQty",rq.subtract(lq));row.put("changeType",l==null?"ADDED":r==null?"REMOVED":lq.compareTo(rq)==0?"UNCHANGED":"CHANGED");out.add(row);}return ApiResponse.ok(out);}

    private List<Map<String,Object>> lines(String code,String version){return jdbc.queryForList("SELECT * FROM src_plm.plm_md_bom_line WHERE bom_code=? AND bom_version=? ORDER BY line_no",code,version);}private BigDecimal decimal(Map<String,Object> row){if(row==null||row.get("qty_per")==null)return BigDecimal.ZERO;return new BigDecimal(String.valueOf(row.get("qty_per")));}
}
