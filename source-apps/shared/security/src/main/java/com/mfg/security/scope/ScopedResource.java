package com.mfg.security.scope;

/** SQL identifiers come only from this server-side registry, never from a request. */
public enum ScopedResource {
    CUSTOMER("src_mdm.md_customer","created_by",null,"customer_code","customer_name","status"),
    SUPPLIER("src_mdm.md_supplier","created_by",null,"supplier_code","supplier_name","status"),
    OPPORTUNITY("src_crm.crm_opportunity","owner_user","dept_code","opportunity_no","opportunity_name","stage_code"),
    SALES_ORDER("src_erp.erp_sales_order","sales_user","dept_code","sales_order_no","customer_name","order_status"),
    WORK_ORDER("src_mes.mes_work_order","workshop_user","workshop_code","work_order_no","product_code","wo_status"),
    EQUIPMENT("src_eam.eam_equipment",null,"workshop_code","equipment_code","equipment_name","equipment_status"),
    INSPECTION("src_qms.qms_inspection","inspector_code","workshop_code","inspection_no","material_code","inspect_result"),
    PURCHASE_ORDER("src_srm.srm_purchase_order","purchase_user",null,"purchase_order_no","supplier_code","order_status");
    public final String table,owner,department,code,label,status;
    ScopedResource(String table,String owner,String department,String code,String label,String status) {
        this.table=table;this.owner=owner;this.department=department;this.code=code;this.label=label;this.status=status;
    }
}
