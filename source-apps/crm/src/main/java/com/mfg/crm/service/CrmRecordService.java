package com.mfg.crm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.principal.LoginUser;
import com.mfg.security.scope.DataScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;

/** CRUD policy for CRM records. Workflow controlled records remain editable only before approval. */
@Service
@RequiredArgsConstructor
public class CrmRecordService {
    private final JdbcTemplate db;
    private final DataScopePolicy scope;

    private record Spec(String table, String owner, Map<String,String> fields) {}
    private static Map<String,String> fields(String... pairs) {
        Map<String,String> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put(pairs[i],pairs[i+1]);
        return Collections.unmodifiableMap(result);
    }
    private Spec spec(String kind) {
        return switch(kind) {
            case "leads" -> new Spec("crm_lead","owner_user",fields(
                    "customerName","customer_name","contactName","contact_name","contactPhone","contact_phone",
                    "contactEmail","contact_email","contactTitle","contact_title","industry","industry",
                    "sourceChannel","source_channel","intentLevel","intent_level","score","score",
                    "expectedBudget","expected_budget","expectedCloseDate","expected_close_date",
                    "preferredContactMethod","preferred_contact_method"));
            case "opportunities" -> new Spec("crm_opportunity","owner_user",fields(
                    "opportunityName","opportunity_name","customerCode","customer_code","expectAmount","expect_amount",
                    "expectSignDate","expect_sign_date","probability","probability","expectedCloseDate","expected_close_date",
                    "contactName","contact_name","leadSource","lead_source","nextStep","next_step",
                    "description","description","forecastCategory","forecast_category","priority","priority","remark","remark"));
            case "quotations" -> new Spec("crm_quotation","created_by",fields(
                    "opportunityNo","opportunity_no","customerCode","customer_code","currency","currency",
                    "taxRate","tax_rate","validUntil","valid_until","paymentTerms","payment_terms",
                    "shipToAddress","ship_to_address","deliveryTerms","delivery_terms","notes","notes"));
            case "contracts" -> new Spec("crm_contract","created_by",fields(
                    "contractName","contract_name","effectiveDate","effective_date","expireDate","expire_date",
                    "deliveryTerms","delivery_terms","paymentTerms","payment_terms","penaltyTerms","penalty_terms",
                    "autoRenew","auto_renew","renewalNoticeDays","renewal_notice_days","signingDate","signing_date",
                    "specialTerms","special_terms","attachmentUrl","attachment_url"));
            case "complaints" -> new Spec("crm_complaint","created_by",fields(
                    "customerCode","customer_code","salesOrderNo","sales_order_no","deliveryNo","delivery_no",
                    "productCode","product_code","batchNo","batch_no","severity","severity","problemDesc","problem_desc",
                    "responsibleDept","responsible_dept","contactEmail","contact_email","sourceChannel","source_channel",
                    "responseDueAt","response_due_at","desiredResolution","desired_resolution"));
            default -> throw BizException.of(ErrorCode.PARAM_INVALID,"不支持的 CRM 单据类型");
        };
    }

    @Transactional(readOnly=true)
    public Map<String,Object> get(String kind,long id) {
        Map<String,Object> row=load(spec(kind),id);
        if("quotations".equals(kind))row.put("lines",db.queryForList("SELECT line_no,product_code,product_name,qty,unit_code,standard_price,sales_price,standard_cost FROM src_crm.crm_quotation_line WHERE quotation_id=? ORDER BY line_no",id));
        if("contracts".equals(kind))row.put("payment_plans",db.queryForList("SELECT period_no,due_date,plan_ratio,plan_amount,received_amount,status FROM src_crm.crm_payment_plan WHERE contract_id=? ORDER BY period_no",id));
        return row;
    }

    @Transactional
    public Map<String,Object> update(String kind,long id,Map<String,Object> body) {
        Spec spec=spec(kind); Map<String,Object> row=load(spec,id); ensureOwner(spec,row);
        ensureEditable(kind,row);
        validateRequiredUpdates(kind,body);
        if("opportunities".equals(kind) && body.containsKey("customerCode")) {
            List<Map<String,Object>> customers=db.queryForList("SELECT customer_name FROM src_mdm.md_customer WHERE customer_code=? AND status='PUBLISHED'",body.get("customerCode"));
            if(customers.isEmpty())throw BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED,"客户不存在或尚未发布");
        }
        if("quotations".equals(kind) && (body.containsKey("opportunityNo") || body.containsKey("customerCode"))) {
            Object opportunityNo=body.getOrDefault("opportunityNo",row.get("opportunity_no"));
            Object customerCode=body.getOrDefault("customerCode",row.get("customer_code"));
            if(opportunityNo==null || customerCode==null || count("SELECT COUNT(*) FROM src_crm.crm_opportunity WHERE opportunity_no=? AND customer_code=?",opportunityNo,customerCode)==0)
                throw BizException.of(ErrorCode.PARAM_INVALID,"关联商机与客户不匹配");
        }
        if("complaints".equals(kind) && body.containsKey("customerCode")) ensurePublishedCustomer(body.get("customerCode"));
        List<String> assignments=new ArrayList<>(); List<Object> args=new ArrayList<>();
        for(var entry:spec.fields().entrySet()) if(body.containsKey(entry.getKey())) {
            assignments.add(entry.getValue()+"=?"); args.add(convert(entry.getValue(),body.get(entry.getKey())));
        }
        if(assignments.isEmpty() && !body.containsKey("lines") && !body.containsKey("paymentPlans"))
            throw BizException.of(ErrorCode.PARAM_INVALID,"没有可保存的字段");
        if("opportunities".equals(kind) && body.containsKey("customerCode")) {
            assignments.add("customer_name=(SELECT customer_name FROM src_mdm.md_customer WHERE customer_code=?)");args.add(body.get("customerCode"));
        }
        if(body.containsKey("lines") && !"quotations".equals(kind))throw BizException.of(ErrorCode.PARAM_INVALID,"只有报价支持维护产品明细");
        if(body.containsKey("paymentPlans") && !"contracts".equals(kind))throw BizException.of(ErrorCode.PARAM_INVALID,"只有合同支持维护回款计划");
        if(!assignments.isEmpty()) { args.add(id); db.update("UPDATE src_crm."+spec.table()+" SET "+String.join(",",assignments)+" WHERE id=?",args.toArray()); }
        if("quotations".equals(kind) && body.containsKey("lines")) updateQuoteLines(id,body.get("lines"));
        if("contracts".equals(kind) && body.containsKey("paymentPlans")) updatePaymentPlans(id,row,body.get("paymentPlans"));
        return load(spec,id);
    }

    @Transactional
    public void delete(String kind,long id) {
        Spec spec=spec(kind); Map<String,Object> row=load(spec,id); ensureOwner(spec,row);
        String status=String.valueOf(row.getOrDefault("status",""));
        switch(kind) {
            case "leads" -> {
                if(row.get("converted_opportunity_no")!=null || !Set.of("NEW","FOLLOWING").contains(status)) locked();
                db.update("DELETE FROM src_crm.crm_lead_follow WHERE lead_id=?",id);
                db.update("DELETE FROM src_crm.crm_lead_assignment WHERE lead_id=?",id);
            }
            case "opportunities" -> {
                if(Set.of("WON","LOST").contains(String.valueOf(row.get("stage_code")))) locked();
                if(count("SELECT COUNT(*) FROM src_crm.crm_quotation WHERE opportunity_no=?",row.get("opportunity_no"))>0) locked();
                db.update("DELETE FROM src_crm.crm_opportunity_follow WHERE opportunity_no=?",row.get("opportunity_no"));
                db.update("DELETE FROM src_crm.crm_opportunity_product WHERE opportunity_no=?",row.get("opportunity_no"));
                db.update("DELETE FROM src_crm.crm_competitor WHERE opportunity_no=?",row.get("opportunity_no"));
            }
            case "quotations" -> {
                if(!"DRAFT".equals(status) || row.get("workflow_instance_id")!=null) locked();
                if(count("SELECT COUNT(*) FROM src_crm.crm_contract WHERE quotation_id=?",id)>0) locked();
                db.update("DELETE FROM src_crm.crm_quotation_line WHERE quotation_id=?",id);
            }
            case "contracts" -> {
                if(!"DRAFT".equals(status) || row.get("workflow_instance_id")!=null || row.get("erp_sales_order_no")!=null) locked();
                db.update("DELETE FROM src_crm.crm_payment_plan WHERE contract_id=?",id);
            }
            case "complaints" -> { if(!"OPEN".equals(status) || row.get("closed_at")!=null) locked(); }
            default -> throw BizException.of(ErrorCode.PARAM_INVALID,"当前单据不支持删除");
        }
        db.update("DELETE FROM src_crm."+spec.table()+" WHERE id=?",id);
    }

    private Map<String,Object> load(Spec spec,long id) {
        var f=scope.filter("src_crm."+spec.table(),"");
        List<Object> args=new ArrayList<>();args.add(id);args.addAll(f.parameters());
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM src_crm."+spec.table()+" WHERE id=? AND ("+f.sql()+")",args.toArray());
        if(rows.isEmpty())throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND,"单据不存在或超出可访问范围");
        Map<String,Object> row=rows.get(0); ensureOwner(spec,row); return row;
    }
    private void ensureOwner(Spec spec,Map<String,Object> row) {
        LoginUser user=CurrentUser.find().orElse(null);
        if(user==null || user.isAdmin() || user.getRoleCodes().contains("SALES_SUPERVISOR") || user.getRoleCodes().contains("SALES_DIRECTOR")) return;
        if(!Objects.equals(user.getUsername(),String.valueOf(row.get(spec.owner()))))
            throw BizException.of(ErrorCode.DATA_SCOPE_DENIED,"只能维护本人负责或创建的 CRM 单据");
    }
    private void ensureEditable(String kind,Map<String,Object> row) {
        String status=String.valueOf(row.getOrDefault("status",""));
        boolean allowed=switch(kind) {
            case "leads" -> Set.of("NEW","FOLLOWING").contains(status) && row.get("converted_opportunity_no")==null;
            case "opportunities" -> !Set.of("WON","LOST").contains(String.valueOf(row.get("stage_code")));
            case "quotations","contracts" -> Set.of("DRAFT","REJECTED").contains(status);
            case "complaints" -> "OPEN".equals(status) && row.get("closed_at")==null;
            default -> false;
        };
        if(!allowed)locked();
    }
    private void validateRequiredUpdates(String kind,Map<String,Object> body) {
        Map<String,String> required=switch(kind) {
            case "leads" -> Map.of("customerName","客户名称");
            case "opportunities" -> Map.of("opportunityName","商机名称","customerCode","客户编码");
            case "complaints" -> Map.of("customerCode","客户编码","problemDesc","问题描述");
            case "contracts" -> Map.of("contractName","合同名称");
            default -> Map.of();
        };
        for(var entry:required.entrySet()) if(body.containsKey(entry.getKey()) &&
                (body.get(entry.getKey())==null || String.valueOf(body.get(entry.getKey())).isBlank()))
            throw BizException.of(ErrorCode.PARAM_INVALID,entry.getValue()+"不能为空");
    }
    private void ensurePublishedCustomer(Object code) {
        if(code==null || db.queryForObject("SELECT COUNT(*) FROM src_mdm.md_customer WHERE customer_code=? AND status='PUBLISHED'",Integer.class,code)==0)
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED,"客户不存在或尚未发布");
    }

    private void updateQuoteLines(long id,Object value) {
        if(!(value instanceof List<?> items) || items.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID,"报价至少需要一条明细");
        BigDecimal amount=BigDecimal.ZERO,cost=BigDecimal.ZERO,standard=BigDecimal.ZERO;int seq=1;
        List<Map<String,Object>> lines=new ArrayList<>();
        for(Object item:items) {
            if(!(item instanceof Map<?,?> raw)) throw BizException.of(ErrorCode.PARAM_INVALID,"报价明细格式错误");
            @SuppressWarnings("unchecked") Map<String,Object> line=(Map<String,Object>)raw;
            String product=string(line,"productCode");
            if(product==null)throw BizException.of(ErrorCode.PARAM_INVALID,"报价明细必须选择产品");
            Integer published=db.queryForObject("SELECT COUNT(*) FROM src_mdm.md_product WHERE product_code=? AND status='PUBLISHED'",Integer.class,product);
            if(published==null||published==0)throw BizException.of(ErrorCode.MASTER_DATA_NOT_PUBLISHED,"报价产品未发布："+product);
            BigDecimal qty=decimal(line,"qty"),price=decimal(line,"salesPrice"),base=decimal(line,"standardPrice"),unitCost=decimal(line,"standardCost");
            if(qty.signum()<=0)throw BizException.of(ErrorCode.PARAM_INVALID,"报价数量必须大于0");
            amount=amount.add(qty.multiply(price));cost=cost.add(qty.multiply(unitCost));standard=standard.add(qty.multiply(base));
            lines.add(line);
        }
        BigDecimal discount=standard.signum()==0?BigDecimal.ZERO:BigDecimal.ONE.subtract(amount.divide(standard,6,java.math.RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(100));
        BigDecimal margin=amount.signum()==0?BigDecimal.ZERO:amount.subtract(cost).divide(amount,6,java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        db.update("DELETE FROM src_crm.crm_quotation_line WHERE quotation_id=?",id);
        for(Map<String,Object> line:lines) {
            BigDecimal qty=decimal(line,"qty"),price=decimal(line,"salesPrice"),unitCost=decimal(line,"standardCost");
            db.update("INSERT INTO src_crm.crm_quotation_line(quotation_id,line_no,product_code,product_name,qty,unit_code,standard_price,sales_price,standard_cost,line_amount,line_cost) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    id,integer(line,"lineNo",seq++),string(line,"productCode"),string(line,"productName"),qty,stringDefault(line,"unitCode","PCS"),decimal(line,"standardPrice"),price,unitCost,qty.multiply(price),qty.multiply(unitCost));
        }
        db.update("UPDATE src_crm.crm_quotation SET total_amount=?,total_cost=?,discount_rate=?,gross_margin_rate=? WHERE id=?",amount,cost,discount,margin,id);
    }
    private void updatePaymentPlans(long id,Map<String,Object> contract,Object value) {
        if(!(value instanceof List<?> items)||items.isEmpty())throw BizException.of(ErrorCode.PARAM_INVALID,"合同至少需要一期回款计划");
        BigDecimal total=BigDecimal.ZERO;
        for(Object item:items) { if(!(item instanceof Map<?,?> raw))throw BizException.of(ErrorCode.PARAM_INVALID,"回款计划格式错误"); @SuppressWarnings("unchecked") Map<String,Object> plan=(Map<String,Object>)raw; total=total.add(decimal(plan,"planAmount")); }
        if(total.compareTo(new BigDecimal(String.valueOf(contract.get("amount"))))!=0)throw BizException.of(ErrorCode.PARAM_INVALID,"回款计划金额合计必须等于合同金额");
        db.update("DELETE FROM src_crm.crm_payment_plan WHERE contract_id=?",id);int seq=1;
        for(Object item:items) { @SuppressWarnings("unchecked") Map<String,Object> plan=(Map<String,Object>)item; Object due=plan.get("dueDate");if(due==null||String.valueOf(due).isBlank())throw BizException.of(ErrorCode.PARAM_INVALID,"回款计划到期日必填");
            db.update("INSERT INTO src_crm.crm_payment_plan(contract_id,period_no,due_date,plan_ratio,plan_amount) VALUES(?,?,?,?,?)",id,integer(plan,"periodNo",seq++),Date.valueOf(String.valueOf(due)),decimal(plan,"planRatio"),decimal(plan,"planAmount")); }
    }
    private Object convert(String column,Object value) {
        if(value==null || (value instanceof String s && s.isBlank())) return null;
        if(column.endsWith("_date"))return Date.valueOf(String.valueOf(value));
        if(column.equals("response_due_at"))return java.sql.Timestamp.valueOf(String.valueOf(value).replace('T',' '));
        if(Set.of("expected_budget","expect_amount","probability","score","auto_renew","renewal_notice_days").contains(column)) {
            if(column.equals("auto_renew"))return Boolean.parseBoolean(String.valueOf(value));
            if(column.equals("score")||column.equals("renewal_notice_days"))return Integer.valueOf(String.valueOf(value));
            return new BigDecimal(String.valueOf(value));
        }
        return value;
    }
    private long count(String sql,Object... values){Long n=db.queryForObject(sql,Long.class,values);return n==null?0:n;}
    private void locked(){throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"单据已进入后续流程或已关联业务，不能编辑或删除；请新建版本或走终止流程");}
    private BigDecimal decimal(Map<String,Object> m,String key){Object v=m.get(key);return v==null||String.valueOf(v).isBlank()?BigDecimal.ZERO:new BigDecimal(String.valueOf(v));}
    private String string(Map<String,Object>m,String key){Object v=m.get(key);return v==null||String.valueOf(v).isBlank()?null:String.valueOf(v).trim();}
    private String stringDefault(Map<String,Object>m,String key,String fallback){String v=string(m,key);return v==null?fallback:v;}
    private int integer(Map<String,Object>m,String key,int fallback){Object v=m.get(key);return v==null?fallback:Integer.parseInt(String.valueOf(v));}
}
