package com.mfg.mdm.domain;

import lombok.Getter;

/**
 * 主数据业务类型。
 *
 * <p>{@link #getApprovalFlow()} 与 {@code mfg_auth.wf_definition.biz_type} 对应，
 * 决定提交审批时走哪条链。
 */
@Getter
public enum BizType {

    CUSTOMER("客户", "MDM_CUSTOMER_NEW", "crm,erp"),
    SUPPLIER("供应商", "MDM_SUPPLIER_NEW", "srm,erp"),
    MATERIAL("物料", "MDM_MATERIAL_NEW", "erp,mes,wms,qms,srm,eam,plm"),
    PRODUCT("产品", "MDM_MATERIAL_NEW", "plm,erp"),
    BOM("BOM", "MDM_BOM_CHANGE", "erp,mes,plm"),
    ROUTING("工艺路线", "MDM_ROUTING_CHANGE", "mes,plm"),
    MATERIAL_CATEGORY("物料分类", null, "erp,mes,wms"),
    UNIT("计量单位", null, "erp,mes,wms"),
    ORG_UNIT("组织", null, "erp,mes"),
    COST_CENTER("成本中心", null, "erp"),
    ACCOUNT_SUBJECT("会计科目", null, "erp"),
    EMPLOYEE("员工", null, "erp");

    /** 中文名 */
    private final String label;

    /** 对应审批流程编码；null 表示无需审批（基础字典类） */
    private final String approvalFlow;

    /** 需要分发到哪些业务系统 */
    private final String targetSystems;

    BizType(String label, String approvalFlow, String targetSystems) {
        this.label = label;
        this.approvalFlow = approvalFlow;
        this.targetSystems = targetSystems;
    }

    /** 是否需要走审批 */
    public boolean needsApproval() {
        return approvalFlow != null;
    }

    public String[] systemArray() {
        return targetSystems.split(",");
    }
}
