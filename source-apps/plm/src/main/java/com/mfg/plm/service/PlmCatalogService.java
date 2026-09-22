package com.mfg.plm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class PlmCatalogService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly=true)
    public Page<Map<String,Object>> page(String type,String keyword,int page,int size){String table=table(type);String[] search=searchColumns(type);int p=Math.max(0,page-1),s=Math.min(200,Math.max(1,size));String where=(keyword==null||keyword.isBlank())?"":" WHERE "+search[0]+" LIKE ? OR "+search[1]+" LIKE ?";List<Map<String,Object>> rows;Long total;if(where.isBlank()){rows=jdbc.queryForList("SELECT * FROM src_plm."+table+" ORDER BY id DESC LIMIT ? OFFSET ?",s,p*s);total=jdbc.queryForObject("SELECT COUNT(*) FROM src_plm."+table,Long.class);}else{String like="%"+keyword.trim()+"%";rows=jdbc.queryForList("SELECT * FROM src_plm."+table+where+" ORDER BY id DESC LIMIT ? OFFSET ?",like,like,s,p*s);total=jdbc.queryForObject("SELECT COUNT(*) FROM src_plm."+table+where,Long.class,like,like);}return new PageImpl<>(rows,PageRequest.of(p,s),total==null?0:total);}

    @Transactional public Map<String,Object> create(String type,Map<String,Object> x){String me=CurrentUser.usernameOrSystem();try{switch(type){case "families"->jdbc.update("INSERT INTO src_plm.plm_product_family(family_code,family_name,market_segment,owner_user,status) VALUES(?,?,?,?,?)",req(x,"familyCode"),req(x,"familyName"),text(x,"marketSegment"),text(x,"ownerUser"),value(x,"status","ACTIVE"));case "versions"->jdbc.update("INSERT INTO src_plm.plm_product_version(product_code,version_no,version_name,lifecycle_status,baseline_no,effective_date,status,created_by) VALUES(?,?,?,?,?,?,?,?)",req(x,"productCode"),req(x,"versionNo"),text(x,"versionName"),value(x,"lifecycleStatus","DESIGN"),text(x,"baselineNo"),date(x,"effectiveDate"),"DRAFT",me);case "documents"->jdbc.update("INSERT INTO src_plm.plm_document(doc_no,doc_name,doc_type,version_no,product_code,file_name,confidentiality,status,created_by) VALUES(?,?,?,?,?,?,?,?,?)",req(x,"docNo"),req(x,"docName"),req(x,"docType"),value(x,"versionNo","V1.0"),text(x,"productCode"),text(x,"fileName"),value(x,"confidentiality","INTERNAL"),"DRAFT",me);case "baselines"->jdbc.update("INSERT INTO src_plm.plm_engineering_baseline(baseline_no,baseline_name,product_code,product_version,bom_code,bom_version,routing_code,routing_version,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",req(x,"baselineNo"),req(x,"baselineName"),req(x,"productCode"),req(x,"productVersion"),text(x,"bomCode"),text(x,"bomVersion"),text(x,"routingCode"),text(x,"routingVersion"),"DRAFT",me);default->throw invalid();}}catch(DuplicateKeyException e){throw BizException.of(ErrorCode.DUPLICATE_KEY,"编码或版本已存在");}return latest(type);}

    @Transactional public Map<String,Object> update(String type,Long id,Map<String,Object>x){ensureEditable(type,id);int n=switch(type){case "families"->jdbc.update("UPDATE src_plm.plm_product_family SET family_name=?,market_segment=?,owner_user=?,status=? WHERE id=?",req(x,"familyName"),text(x,"marketSegment"),text(x,"ownerUser"),value(x,"status","ACTIVE"),id);case "versions"->jdbc.update("UPDATE src_plm.plm_product_version SET version_name=?,lifecycle_status=?,baseline_no=?,effective_date=? WHERE id=?",text(x,"versionName"),value(x,"lifecycleStatus","DESIGN"),text(x,"baselineNo"),date(x,"effectiveDate"),id);case "documents"->jdbc.update("UPDATE src_plm.plm_document SET doc_name=?,doc_type=?,product_code=?,file_name=?,confidentiality=? WHERE id=?",req(x,"docName"),req(x,"docType"),text(x,"productCode"),text(x,"fileName"),value(x,"confidentiality","INTERNAL"),id);case "baselines"->jdbc.update("UPDATE src_plm.plm_engineering_baseline SET baseline_name=?,product_code=?,product_version=?,bom_code=?,bom_version=?,routing_code=?,routing_version=? WHERE id=?",req(x,"baselineName"),req(x,"productCode"),req(x,"productVersion"),text(x,"bomCode"),text(x,"bomVersion"),text(x,"routingCode"),text(x,"routingVersion"),id);default->throw invalid();};if(n==0)throw BizException.notFound("PLM数据",id);return byId(type,id);}

    @Transactional public Map<String,Object> release(String type,Long id){if("families".equals(type))throw BizException.conflict("产品族无需发布");ensureEditable(type,id);String table=table(type);String extra=("documents".equals(type)||"baselines".equals(type))?",released_by=?,released_at=?":"";int n=jdbc.update("UPDATE src_plm."+table+" SET status='RELEASED'"+extra+" WHERE id=?",("documents".equals(type)||"baselines".equals(type))?new Object[]{CurrentUser.usernameOrSystem(),LocalDateTime.now(),id}:new Object[]{id});if(n==0)throw BizException.notFound("PLM数据",id);return byId(type,id);}

    private void ensureEditable(String type,Long id){Map<String,Object> row=byId(type,id);Object status=row.get("status");if(status!=null&&!List.of("DRAFT","ACTIVE").contains(String.valueOf(status)))throw BizException.conflict("已发布数据不可直接修改，请通过工程变更建立新版本");}
    private Map<String,Object> latest(String type){return jdbc.queryForMap("SELECT * FROM src_plm."+table(type)+" ORDER BY id DESC LIMIT 1");}private Map<String,Object> byId(String type,Long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM src_plm."+table(type)+" WHERE id=?",id);if(rows.isEmpty())throw BizException.notFound("PLM数据",id);return rows.get(0);}
    private String table(String type){return switch(type){case"families"->"plm_product_family";case"versions"->"plm_product_version";case"documents"->"plm_document";case"baselines"->"plm_engineering_baseline";default->throw invalid();};}private String[] searchColumns(String type){return switch(type){case"families"->new String[]{"family_code","family_name"};case"versions"->new String[]{"product_code","version_name"};case"documents"->new String[]{"doc_no","doc_name"};case"baselines"->new String[]{"baseline_no","baseline_name"};default->throw invalid();};}
    private BizException invalid(){return BizException.of(ErrorCode.PARAM_INVALID,"未知PLM目录类型");}private Object req(Map<String,Object>x,String k){Object v=x.get(k);if(v==null||String.valueOf(v).isBlank())throw BizException.of(ErrorCode.PARAM_INVALID,k+"不能为空");return v;}private String text(Map<String,Object>x,String k){Object v=x.get(k);return v==null||String.valueOf(v).isBlank()?null:String.valueOf(v);}private String value(Map<String,Object>x,String k,String d){String v=text(x,k);return v==null?d:v;}private Date date(Map<String,Object>x,String k){String v=text(x,k);return v==null?null:Date.valueOf(v);}
}
