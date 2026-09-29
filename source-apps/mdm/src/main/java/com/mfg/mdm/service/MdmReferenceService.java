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
                case "categories" -> createCategory(input, me);
                case "units" -> {
                    validateUnit(input, null);
                    jdbc.update("INSERT INTO src_mdm.md_unit(unit_code,unit_name,unit_type,base_unit_code,convert_rate,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,'PUBLISHED',1,?,?)", req(input,"unitCode"),req(input,"unitName"),req(input,"unitType"),text(input,"baseUnitCode"),decimal(input,"convertRate",BigDecimal.ONE),me,me);
                }
                case "organizations" -> jdbc.update("INSERT INTO src_mdm.md_org_unit(org_code,org_name,org_type,parent_id,org_level,manager_user_id,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,?,'PUBLISHED',1,?,?)",req(input,"orgCode"),req(input,"orgName"),req(input,"orgType"),number(input,"parentId"),integer(input,"orgLevel",1),number(input,"managerUserId"),me,me);
                case "cost-centers" -> jdbc.update("INSERT INTO src_mdm.md_cost_center(cc_code,cc_name,org_id,cc_type,manager_emp_id,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,'PUBLISHED',1,?,?)",req(input,"ccCode"),req(input,"ccName"),number(input,"orgId"),text(input,"ccType"),number(input,"managerEmpId"),me,me);
                case "subjects" -> jdbc.update("INSERT INTO src_mdm.md_account_subject(subject_code,subject_name,subject_type,parent_id,subject_level,is_leaf,status,version_no,created_by,updated_by) VALUES(?,?,?,?,?,?,'PUBLISHED',1,?,?)",req(input,"subjectCode"),req(input,"subjectName"),req(input,"subjectType"),number(input,"parentId"),integer(input,"subjectLevel",1),bool(input,"leaf",true),me,me);
                default -> throw invalidType();
            }
        } catch (DuplicateKeyException ex) { throw BizException.of(ErrorCode.DUPLICATE_KEY, "编码已存在"); }
        return byCode(type, code(type, input));
    }

    @Transactional
    public Map<String, Object> update(String type, Long id, Map<String, Object> input) {
        String me = CurrentUser.usernameOrSystem();
        int rows = switch (type) {
            case "categories" -> updateCategory(id, input, me);
            case "units" -> {
                validateUnit(input, id);
                yield jdbc.update("UPDATE src_mdm.md_unit SET unit_name=?,unit_type=?,base_unit_code=?,convert_rate=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"unitName"),req(input,"unitType"),text(input,"baseUnitCode"),decimal(input,"convertRate",BigDecimal.ONE),me,id);
            }
            case "organizations" -> jdbc.update("UPDATE src_mdm.md_org_unit SET org_name=?,org_type=?,parent_id=?,org_level=?,manager_user_id=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"orgName"),req(input,"orgType"),number(input,"parentId"),integer(input,"orgLevel",1),number(input,"managerUserId"),me,id);
            case "cost-centers" -> jdbc.update("UPDATE src_mdm.md_cost_center SET cc_name=?,org_id=?,cc_type=?,manager_emp_id=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"ccName"),number(input,"orgId"),text(input,"ccType"),number(input,"managerEmpId"),me,id);
            case "subjects" -> jdbc.update("UPDATE src_mdm.md_account_subject SET subject_name=?,subject_type=?,parent_id=?,subject_level=?,is_leaf=?,version_no=version_no+1,updated_by=? WHERE id=?",req(input,"subjectName"),req(input,"subjectType"),number(input,"parentId"),integer(input,"subjectLevel",1),bool(input,"leaf",true),me,id);
            default -> throw invalidType();
        };
        if (rows == 0) throw BizException.notFound("主数据", id);
        return byId(type, id);
    }

    private void createCategory(Map<String, Object> input, String operator) {
        String name = String.valueOf(req(input, "categoryName")).trim();
        Long parentId = number(input, "parentId");
        CategoryParent parent = parent(parentId);
        String path = parent == null ? name : parent.path() + "/" + name;
        int level = parent == null ? 1 : parent.level() + 1;
        jdbc.update("""
                INSERT INTO src_mdm.md_material_category
                    (category_code,category_name,parent_id,category_level,is_leaf,category_path,status,version_no,created_by,updated_by)
                VALUES (?,?,?,?,?,?,'PUBLISHED',1,?,?)
                """, req(input, "categoryCode"), name, parentId, level, bool(input, "leaf", true), path, operator, operator);
        if (parentId != null) jdbc.update("UPDATE src_mdm.md_material_category SET is_leaf=0 WHERE id=?", parentId);
    }

    private void validateUnit(Map<String, Object> input, Long currentId) {
        String code = String.valueOf(req(input, "unitCode")).trim();
        String name = String.valueOf(req(input, "unitName")).trim();
        String unitType = String.valueOf(req(input, "unitType")).trim();
        if (name.isEmpty() || unitType.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "单位名称和单位类型不能为空");

        if (currentId != null) {
            String storedCode;
            try { storedCode = jdbc.queryForObject("SELECT unit_code FROM src_mdm.md_unit WHERE id=?", String.class, currentId); }
            catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw BizException.notFound("计量单位", currentId); }
            if (!storedCode.equals(code)) throw BizException.of(ErrorCode.PARAM_INVALID, "单位编码创建后不可修改");
        }

        BigDecimal rate = decimal(input, "convertRate", BigDecimal.ONE);
        if (rate.signum() <= 0) throw BizException.of(ErrorCode.PARAM_INVALID, "换算率必须大于0");

        String baseCode = text(input, "baseUnitCode");
        if (baseCode == null) return;
        if (baseCode.equals(code)) throw BizException.of(ErrorCode.PARAM_INVALID, "换算基准单位不能指向自身");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_unit WHERE unit_code=? AND status='PUBLISHED'", Integer.class, baseCode);
        if (active == null || active == 0) throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "换算基准单位不存在或已停用");

        java.util.Set<String> visited = new java.util.HashSet<>();
        String cursor = baseCode;
        while (cursor != null && !cursor.isBlank() && visited.add(cursor)) {
            if (cursor.equals(code)) throw BizException.of(ErrorCode.PARAM_INVALID, "单位换算关系不能形成循环");
            try { cursor = jdbc.queryForObject("SELECT base_unit_code FROM src_mdm.md_unit WHERE unit_code=?", String.class, cursor); }
            catch (org.springframework.dao.EmptyResultDataAccessException ex) { break; }
        }
    }

    private int updateCategory(Long id, Map<String, Object> input, String operator) {
        Map<String, Object> existing;
        try { existing = byId("categories", id); }
        catch (org.springframework.dao.EmptyResultDataAccessException ex) { return 0; }
        String oldPath = String.valueOf(existing.get("category_path"));
        int oldLevel = ((Number) existing.get("category_level")).intValue();
        Long oldParentId = existing.get("parent_id") == null ? null : ((Number) existing.get("parent_id")).longValue();
        Long parentId = number(input, "parentId");
        if (id.equals(parentId)) throw BizException.of(ErrorCode.PARAM_INVALID, "分类不能选择自身作为上级");

        CategoryParent parent = parent(parentId);
        if (parent != null && (parent.path().equals(oldPath) || parent.path().startsWith(oldPath + "/"))) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "不能把下级分类设为上级分类");
        }
        String name = String.valueOf(req(input, "categoryName")).trim();
        String path = parent == null ? name : parent.path() + "/" + name;
        int level = parent == null ? 1 : parent.level() + 1;
        Integer children = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_material_category WHERE parent_id=? AND status<>'DISABLED'", Integer.class, id);
        boolean leaf = bool(input, "leaf", true);
        if (Boolean.TRUE.equals(leaf) && children != null && children > 0) {
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "该分类存在下级分类，不能设为末级");
        }
        int updated = jdbc.update("""
                UPDATE src_mdm.md_material_category
                SET category_name=?,parent_id=?,category_level=?,is_leaf=?,category_path=?,version_no=version_no+1,updated_by=?
                WHERE id=?
                """, name, parentId, level, leaf, path, operator, id);
        if (updated == 0) return 0;

        if (!oldPath.equals(path)) {
            jdbc.update("""
                    UPDATE src_mdm.md_material_category
                    SET category_path=CONCAT(?,SUBSTRING(category_path,CHAR_LENGTH(?)+1)), category_level=category_level+?,
                        version_no=version_no+1, updated_by=?
                    WHERE LEFT(category_path,CHAR_LENGTH(?))=CONCAT(?,'/') AND status<>'DISABLED'
                    """, path, oldPath, level - oldLevel, operator, oldPath, oldPath);
        }
        if (parentId != null) jdbc.update("UPDATE src_mdm.md_material_category SET is_leaf=0 WHERE id=?", parentId);
        if (oldParentId != null && !oldParentId.equals(parentId)) {
            Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_material_category WHERE parent_id=? AND status<>'DISABLED'", Integer.class, oldParentId);
            if (remaining != null && remaining == 0) jdbc.update("UPDATE src_mdm.md_material_category SET is_leaf=1 WHERE id=?", oldParentId);
        }
        return updated;
    }

    private CategoryParent parent(Long id) {
        if (id == null) return null;
        try {
            Map<String, Object> row = jdbc.queryForMap("SELECT category_path,category_level FROM src_mdm.md_material_category WHERE id=? AND status='PUBLISHED'", id);
            return new CategoryParent(String.valueOf(row.get("category_path")), ((Number) row.get("category_level")).intValue());
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND, "上级分类不存在或已停用");
        }
    }

    private record CategoryParent(String path, int level) { }

    @Transactional
    public void disable(String type, Long id) {
        if ("categories".equals(type)) {
            Integer materials = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_material WHERE category_id=?", Integer.class, id);
            Integer products = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_product WHERE category_id=?", Integer.class, id);
            Integer children = jdbc.queryForObject("SELECT COUNT(*) FROM src_mdm.md_material_category WHERE parent_id=? AND status<>'DISABLED'", Integer.class, id);
            if ((materials != null && materials > 0) || (products != null && products > 0) || (children != null && children > 0)) {
                throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "该分类仍被物料、产品或下级分类引用，不能停用");
            }
        }
        if ("units".equals(type)) {
            String unitCode;
            try { unitCode = jdbc.queryForObject("SELECT unit_code FROM src_mdm.md_unit WHERE id=?", String.class, id); }
            catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw BizException.notFound("计量单位", id); }
            Integer used = jdbc.queryForObject("""
                    SELECT (SELECT COUNT(*) FROM src_mdm.md_material WHERE base_unit_code=? OR purchase_unit_code=?)
                         + (SELECT COUNT(*) FROM src_mdm.md_product WHERE unit_code=?)
                         + (SELECT COUNT(*) FROM src_mdm.md_bom_line WHERE unit_code=?)
                         + (SELECT COUNT(*) FROM src_mdm.md_unit WHERE base_unit_code=?)
                    """, Integer.class, unitCode, unitCode, unitCode, unitCode, unitCode);
            if (used != null && used > 0) {
                throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "该单位仍被物料、产品、BOM或换算关系引用，不能停用");
            }
        }
        int rows = jdbc.update("UPDATE src_mdm." + table(type) + " SET status='DISABLED',version_no=version_no+1,updated_by=? WHERE id=?", CurrentUser.usernameOrSystem(), id);
        if (rows == 0) throw BizException.notFound("主数据", id);
    }

    private Map<String, Object> byCode(String type, Object code) { return jdbc.queryForMap("SELECT * FROM src_mdm." + table(type) + " WHERE " + codeColumn(type) + "=?", code); }
    private Map<String, Object> byId(String type, Long id) { return jdbc.queryForMap("SELECT * FROM src_mdm." + table(type) + " WHERE id=?", id); }
    private Object code(String type, Map<String,Object> input) { return req(input, switch(type){case "categories"->"categoryCode";case "units"->"unitCode";case "organizations"->"orgCode";case "cost-centers"->"ccCode";case "subjects"->"subjectCode";default->throw invalidType();}); }
    private String codeColumn(String type) { return switch(type){case "categories"->"category_code";case "units"->"unit_code";case "organizations"->"org_code";case "cost-centers"->"cc_code";case "subjects"->"subject_code";default->throw invalidType();}; }
    private String table(String type) { return switch(type){case "categories"->"md_material_category";case "units"->"md_unit";case "organizations"->"md_org_unit";case "cost-centers"->"md_cost_center";case "subjects"->"md_account_subject";default->throw invalidType();}; }
    private BizException invalidType(){return BizException.of(ErrorCode.PARAM_INVALID,"未知主数据类型");}
    private Object req(Map<String,Object> m,String key){Object value=m.get(key);if(value==null||String.valueOf(value).isBlank())throw BizException.of(ErrorCode.PARAM_INVALID,key+"不能为空");return value;}
    private String text(Map<String,Object> m,String key){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?null:String.valueOf(value);}
    private Long number(Map<String,Object> m,String key){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?null:Long.valueOf(String.valueOf(value));}
    private Integer integer(Map<String,Object> m,String key,int fallback){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?fallback:Integer.valueOf(String.valueOf(value));}
    private BigDecimal decimal(Map<String,Object> m,String key,BigDecimal fallback){Object value=m.get(key);return value==null||String.valueOf(value).isBlank()?fallback:new BigDecimal(String.valueOf(value));}
    private boolean bool(Map<String,Object> m,String key,boolean fallback){Object value=m.get(key);return value==null?fallback:Boolean.parseBoolean(String.valueOf(value));}
}
