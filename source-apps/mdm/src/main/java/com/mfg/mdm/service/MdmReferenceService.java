package com.mfg.mdm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MdmReferenceService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String type) {
        String table = table(type);
        return jdbc.queryForList("SELECT * FROM src_mdm." + table + " ORDER BY id DESC LIMIT 500");
    }

    @Transactional
    public Map<String, Object> create(String type, Map<String, Object> input) {
        String me = CurrentUser.usernameOrSystem();
        try {
            switch (type) {
                case "categories" -> jdbc.update("INSERT INTO src_mdm.md_material_category(category_code,category_name,parent_id,category_level,is_leaf,category_path,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,?,'PUBLISHED',1,?,?)", req(input,"categoryCode"),req(input,"categoryName"),number(input,"parentId"),integer(input,"categoryLevel",1),bool(input,"leaf",true),text(input,"categoryPath"),me,me);
                case "units" -> jdbc.update("INSERT INTO src_mdm.md_unit(unit_code,unit_name,unit_type,base_unit_code,convert_rate,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,'PUBLISHED',1,?,?)", req(input,"unitCode"),req(input,"unitName"),req(input,"unitType"),text(input,"baseUnitCode"),decimal(input,"convertRate",BigDecimal.ONE),me,me);
                case "organizations" -> jdbc.update("INSERT INTO src_mdm.md_org_unit(org_code,org_name,org_type,parent_id,org_level,manager_user_id,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,?,'PUBLISHED',1,?,?)",req(input,"orgCode"),req(input,"orgName"),req(input,"orgType"),number(input,"parentId"),integer(input,"orgLevel",1),number(input,"managerUserId"),me,me);
                case "cost-centers" -> jdbc.update("INSERT INTO src_mdm.md_cost_center(cc_code,cc_name,org_id,cc_type,manager_emp_id,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,'PUBLISHED',1,?,?)",req(input,"ccCode"),req(input,"ccName"),number(input,"orgId"),text(input,"ccType"),number(input,"managerEmpId"),me,me);
                case "subjects" -> jdbc.update("INSERT INTO src_mdm.md_account_subject(subject_code,subject_name,subject_type,parent_id,subject_level,is_leaf,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,?,'PUBLISHED',1,?,?)",req(input,"subjectCode"),req(input,"subjectName"),req(input,"subjectType"),number(input,"parentId"),integer(input,"subjectLevel",1),bool(input,"leaf",true),me,me);
                default -> throw invalidType();
            }
        } catch (DuplicateKeyException ex) { throw BizException.of(ErrorCode.DUPLICATE_KEY, "编码已存在"); }
        return latest(type);
    }

    @Transactional
    public Map<String, Object> update(String type, Long id, Map<String, Object> input) {
        String me = CurrentUser.usernameOrSystem();
        int rows = switch (type) {
            case "categories" -> jdbc.update("UPDATE src_mdm.md_material_category SET category_name=?,parent_id=?,category_level=?,is_leaf=?,category_path=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"categoryName"),number(input,"parentId"),integer(input,"categoryLevel",1),bool(input,"leaf",true),text(input,"categoryPath"),me,id);
            case "units" -> jdbc.update("UPDATE src_mdm.md_unit SET unit_name=?,unit_type=?,base_unit_code=?,convert_rate=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"unitName"),req(input,"unitType"),text(input,"baseUnitCode"),decimal(input,"convertRate",BigDecimal.ONE),me,id);
            case "organizations" -> jdbc.update("UPDATE src_mdm.md_org_unit SET org_name=?,org_type=?,parent_id=?,org_level=?,manager_user_id=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"orgName"),req(input,"orgType"),number(input,"parentId"),integer(input,"orgLevel",1),number(input,"managerUserId"),me,id);
            case "cost-centers" -> jdbc.update("UPDATE src_mdm.md_cost_center SET cc_name=?,org_id=?,cc_type=?,manager_emp_id=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"ccName"),number(input,"orgId"),text(input,"ccType"),number(input,"managerEmpId"),me,id);
            case "subjects" -> jdbc.update("UPDATE src_mdm.md_account_subject SET subject_name=?,subject_type=?,parent_id=?,subject_level=?,is_leaf=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"subjectName"),req(input,"subjectType"),number(input,"parentId"),integer(input,"subjectLevel",1),bool(input,"leaf",true),me,id);
            default -> throw invalidType();
        };
        if (rows == 0) throw BizException.notFound("主数据", id);
        return byId(type, id);
    }

    @Transactional
    public void disable(String type, Long id) {
        int rows = jdbc.update("UPDATE src_mdm." + table(type) + " SET status='DISABLED',version_no=version_no+1,updated_by=? WHERE id=?", CurrentUser.usernameOrSystem(), id);
        if (rows == 0) throw BizException.notFound("主数据", id);
    }

    private Map<String, Object> latest(String type) { return jdbc.queryForMap("SELECT * FROM src_mdm." + table(type) + " ORDER BY id DESC LIMIT 1"); }
    private Map<String, Object> byId(String type, Long id) { return jdbc.queryForMap("SELECT * FROM src_mdm." + table(type) + " WHERE id=?", id); }
    private String table(String type) { return switch(type){case "categories"->"md_material_category";case "units"->"md_unit";case "organizations"->"md_org_unit";case "cost-centers"->"md_cost_center";case "subjects"->"md_account_subject";default->throw invalidType();}; }
    private BizException invalidType(){return BizException.of(ErrorCode.PARAM_INVALID,"未知主数据类型");}
    private Object req(Map<String,Object> m,String key){Object value=m.get(key);if(value==null||String.valueOf(value).isBlank())throw BizException.of(ErrorCode.PARAM_INVALID,key+"不能为空");return value;}
    private String text(Map<String,Object> m,String key){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?null:String.valueOf(value);}
    private Long number(Map<String,Object> m,String key){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?null:Long.valueOf(String.valueOf(value));}
    private Integer integer(Map<String,Object> m,String key,int fallback){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?fallback:Integer.valueOf(String.valueOf(value));}
    private BigDecimal decimal(Map<String,Object> m,String key,BigDecimal fallback){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?fallback:new BigDecimal(String.valueOf(value));}
    private boolean bool(Map<String,Object> m,String key,boolean fallback){Object value=m.get(key);return value==null?fallback:Boolean.parseBoolean(String.valueOf(value));}
}
