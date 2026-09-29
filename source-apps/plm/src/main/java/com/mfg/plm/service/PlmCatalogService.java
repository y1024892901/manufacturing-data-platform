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

@Service
@RequiredArgsConstructor
public class PlmCatalogService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> page(String type, String keyword, int page, int size) {
        String table = table(type);
        String[] search = searchColumns(type);
        int p = Math.max(0, page - 1);
        int s = Math.min(200, Math.max(1, size));
        String where = keyword == null || keyword.isBlank()
                ? ""
                : " WHERE " + search[0] + " LIKE ? OR " + search[1] + " LIKE ?";
        List<Map<String, Object>> rows;
        Long total;
        if (where.isBlank()) {
            rows = jdbc.queryForList("SELECT * FROM src_plm." + table + " ORDER BY id DESC LIMIT ? OFFSET ?", s, p * s);
            total = jdbc.queryForObject("SELECT COUNT(*) FROM src_plm." + table, Long.class);
        } else {
            String like = "%" + keyword.trim() + "%";
            rows = jdbc.queryForList("SELECT * FROM src_plm." + table + where + " ORDER BY id DESC LIMIT ? OFFSET ?", like, like, s, p * s);
            total = jdbc.queryForObject("SELECT COUNT(*) FROM src_plm." + table + where, Long.class, like, like);
        }
        return new PageImpl<>(rows, PageRequest.of(p, s), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String type, Long id) {
        return byId(type, id);
    }

    @Transactional
    public Map<String, Object> create(String type, Map<String, Object> x) {
        String me = CurrentUser.usernameOrSystem();
        try {
            switch (type) {
                case "families" -> jdbc.update("INSERT INTO src_plm.plm_product_family(family_code,family_name,market_segment,owner_user,family_description,status) VALUES(?,?,?,?,?,?)",
                        req(x, "familyCode"), req(x, "familyName"), text(x, "marketSegment"), text(x, "ownerUser"), text(x, "familyDescription"), value(x, "status", "ACTIVE"));
                case "versions" -> jdbc.update("INSERT INTO src_plm.plm_product_version(product_code,version_no,version_name,lifecycle_status,baseline_no,effective_date,change_summary,target_market,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        req(x, "productCode"), req(x, "versionNo"), text(x, "versionName"), value(x, "lifecycleStatus", "DESIGN"), text(x, "baselineNo"), date(x, "effectiveDate"), text(x, "changeSummary"), text(x, "targetMarket"), "DRAFT", me);
                case "documents" -> jdbc.update("INSERT INTO src_plm.plm_document(doc_no,doc_name,doc_type,version_no,product_code,file_name,confidentiality,language_code,effective_date,description,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        req(x, "docNo"), req(x, "docName"), req(x, "docType"), value(x, "versionNo", "V1.0"), text(x, "productCode"), text(x, "fileName"), value(x, "confidentiality", "INTERNAL"), value(x, "languageCode", "zh-CN"), date(x, "effectiveDate"), text(x, "description"), "DRAFT", me);
                case "baselines" -> jdbc.update("INSERT INTO src_plm.plm_engineering_baseline(baseline_no,baseline_name,product_code,product_version,bom_code,bom_version,routing_code,routing_version,baseline_purpose,remark,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        req(x, "baselineNo"), req(x, "baselineName"), req(x, "productCode"), req(x, "productVersion"), text(x, "bomCode"), text(x, "bomVersion"), text(x, "routingCode"), text(x, "routingVersion"), text(x, "baselinePurpose"), text(x, "remark"), "DRAFT", me);
                default -> throw invalid();
            }
        } catch (DuplicateKeyException e) {
            throw BizException.of(ErrorCode.DUPLICATE_KEY, "编码或版本已存在");
        }
        return created(type, x);
    }

    @Transactional
    public Map<String, Object> update(String type, Long id, Map<String, Object> x) {
        ensureEditable(type, id);
        try {
            int n = switch (type) {
                case "families" -> jdbc.update("UPDATE src_plm.plm_product_family SET family_name=?,market_segment=?,owner_user=?,family_description=?,status=? WHERE id=?",
                        req(x, "familyName"), text(x, "marketSegment"), text(x, "ownerUser"), text(x, "familyDescription"), value(x, "status", "ACTIVE"), id);
                case "versions" -> jdbc.update("UPDATE src_plm.plm_product_version SET version_name=?,lifecycle_status=?,baseline_no=?,effective_date=?,change_summary=?,target_market=? WHERE id=?",
                        text(x, "versionName"), value(x, "lifecycleStatus", "DESIGN"), text(x, "baselineNo"), date(x, "effectiveDate"), text(x, "changeSummary"), text(x, "targetMarket"), id);
                case "documents" -> jdbc.update("UPDATE src_plm.plm_document SET doc_name=?,doc_type=?,product_code=?,file_name=?,confidentiality=?,language_code=?,effective_date=?,description=? WHERE id=?",
                        req(x, "docName"), req(x, "docType"), text(x, "productCode"), text(x, "fileName"), value(x, "confidentiality", "INTERNAL"), value(x, "languageCode", "zh-CN"), date(x, "effectiveDate"), text(x, "description"), id);
                case "baselines" -> jdbc.update("UPDATE src_plm.plm_engineering_baseline SET baseline_name=?,product_code=?,product_version=?,bom_code=?,bom_version=?,routing_code=?,routing_version=?,baseline_purpose=?,remark=? WHERE id=?",
                        req(x, "baselineName"), req(x, "productCode"), req(x, "productVersion"), text(x, "bomCode"), text(x, "bomVersion"), text(x, "routingCode"), text(x, "routingVersion"), text(x, "baselinePurpose"), text(x, "remark"), id);
                default -> throw invalid();
            };
            if (n == 0) throw BizException.notFound("PLM数据", id);
        } catch (DuplicateKeyException e) {
            throw BizException.of(ErrorCode.DUPLICATE_KEY, "编码或版本已存在");
        }
        return byId(type, id);
    }

    @Transactional
    public void delete(String type, Long id) {
        Map<String, Object> row = byId(type, id);
        String status = String.valueOf(row.get("status"));
        if ("families".equals(type)) {
            if (!List.of("ACTIVE", "DISABLED", "DRAFT").contains(status)) {
                throw BizException.conflict("该产品族状态不允许删除");
            }
        } else if (!List.of("DRAFT", "ACTIVE").contains(status)) {
            throw BizException.conflict("已发布或进入流程的数据不能删除");
        }

        if ("versions".equals(type)) {
            Long references = jdbc.queryForObject("SELECT COUNT(*) FROM src_plm.plm_engineering_baseline WHERE product_code=? AND product_version=?", Long.class, row.get("product_code"), row.get("version_no"));
            Long documents = jdbc.queryForObject("SELECT COUNT(*) FROM src_plm.plm_document WHERE product_code=? AND version_no=?", Long.class, row.get("product_code"), row.get("version_no"));
            if ((references != null && references > 0) || (documents != null && documents > 0)) {
                throw BizException.conflict("产品版本已被工程基线或受控文档引用，不能删除");
            }
        }
        if ("baselines".equals(type)) {
            Long references = jdbc.queryForObject("SELECT COUNT(*) FROM src_plm.plm_product_version WHERE baseline_no=?", Long.class, row.get("baseline_no"));
            if (references != null && references > 0) throw BizException.conflict("工程基线已被产品版本引用，不能删除");
        }
        jdbc.update("DELETE FROM src_plm." + table(type) + " WHERE id=?", id);
    }

    @Transactional
    public Map<String, Object> release(String type, Long id) {
        if ("families".equals(type)) throw BizException.conflict("产品族无需发布");
        ensureEditable(type, id);
        String table = table(type);
        String extra = ("documents".equals(type) || "baselines".equals(type)) ? ",released_by=?,released_at=?" : "";
        int n = jdbc.update("UPDATE src_plm." + table + " SET status='RELEASED'" + extra + " WHERE id=?",
                ("documents".equals(type) || "baselines".equals(type))
                        ? new Object[]{CurrentUser.usernameOrSystem(), LocalDateTime.now(), id}
                        : new Object[]{id});
        if (n == 0) throw BizException.notFound("PLM数据", id);
        return byId(type, id);
    }

    private void ensureEditable(String type, Long id) {
        Map<String, Object> row = byId(type, id);
        Object status = row.get("status");
        if (status != null && !List.of("DRAFT", "ACTIVE").contains(String.valueOf(status))) {
            throw BizException.conflict("已发布数据不可直接修改，请通过工程变更建立新版本");
        }
    }

    private Map<String, Object> created(String type, Map<String, Object> input) {
        return switch (type) {
            case "families" -> jdbc.queryForMap("SELECT * FROM src_plm.plm_product_family WHERE family_code=?", req(input, "familyCode"));
            case "versions" -> jdbc.queryForMap("SELECT * FROM src_plm.plm_product_version WHERE product_code=? AND version_no=?", req(input, "productCode"), req(input, "versionNo"));
            case "documents" -> jdbc.queryForMap("SELECT * FROM src_plm.plm_document WHERE doc_no=? AND version_no=?", req(input, "docNo"), value(input, "versionNo", "V1.0"));
            case "baselines" -> jdbc.queryForMap("SELECT * FROM src_plm.plm_engineering_baseline WHERE baseline_no=?", req(input, "baselineNo"));
            default -> throw invalid();
        };
    }

    private Map<String, Object> byId(String type, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM src_plm." + table(type) + " WHERE id=?", id);
        if (rows.isEmpty()) throw BizException.notFound("PLM数据", id);
        return rows.get(0);
    }

    private String table(String type) {
        return switch (type) {
            case "families" -> "plm_product_family";
            case "versions" -> "plm_product_version";
            case "documents" -> "plm_document";
            case "baselines" -> "plm_engineering_baseline";
            default -> throw invalid();
        };
    }

    private String[] searchColumns(String type) {
        return switch (type) {
            case "families" -> new String[]{"family_code", "family_name"};
            case "versions" -> new String[]{"product_code", "version_name"};
            case "documents" -> new String[]{"doc_no", "doc_name"};
            case "baselines" -> new String[]{"baseline_no", "baseline_name"};
            default -> throw invalid();
        };
    }

    private BizException invalid() {
        return BizException.of(ErrorCode.PARAM_INVALID, "未知PLM目录类型");
    }

    private Object req(Map<String, Object> x, String key) {
        Object value = x.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw BizException.of(ErrorCode.PARAM_INVALID, key + "不能为空");
        return value;
    }

    private String text(Map<String, Object> x, String key) {
        Object value = x.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private String value(Map<String, Object> x, String key, String fallback) {
        String value = text(x, key);
        return value == null ? fallback : value;
    }

    private Date date(Map<String, Object> x, String key) {
        String value = text(x, key);
        return value == null ? null : Date.valueOf(value);
    }
}
