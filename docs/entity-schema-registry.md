# 实体与 SQL 管理表登记

> 2026-09-24；机器可读清单见 `ops/entity_schema_registry.json`。实时检查发现新增列或依据文件缺失时失败；planned 不计为完成。

| 表 | 管理方式 | 归属计划 | 依据与取舍 |
|---|---|---|---|
| mfg_ads.ads_business_glossary | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ads.ads_dq_issue | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ads.ads_dq_rule_pass_rate | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ads.ads_lineage_snapshot | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ads.ads_recon_diff | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_app.app_agent_session | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_app.app_disposition | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_app.app_llm_call_log | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_app.app_mcp_call_log | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_app.app_vector_store | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_auth.sys_data_scope | sql | 00 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/security/src/main/java/com/mfg/security/scope/DataScopeAspect.java` |
| mfg_auth.sys_role_permission | association | 00 | 由 SysUser/SysRole 的 JPA 关联注解维护的连接表，无独立领域实体。 依据：`source-apps/shared/security/src/main/java/com/mfg/security/entity/SysRole.java` |
| mfg_auth.sys_user_role | association | 00 | 由 SysUser/SysRole 的 JPA 关联注解维护的连接表，无独立领域实体。 依据：`source-apps/shared/security/src/main/java/com/mfg/security/entity/SysUser.java` |
| mfg_auth.sys_user_system | association | 00 | 由 SysUser/SysRole 的 JPA 关联注解维护的连接表，无独立领域实体。 依据：`source-apps/shared/security/src/main/java/com/mfg/security/entity/SysUser.java` |
| mfg_dwd.dim_key_mapping | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_dws.metric_definition | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_meta.meta_audit_log | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_meta.meta_column | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_meta.meta_rule | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_meta.meta_system | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_meta.meta_table | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ods.ods_change_log | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ods.ods_table_registry | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ops.biz_consumer_offset | sql | 00 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/integration/BusinessEventConsumerScheduler.java` |
| mfg_ops.biz_inbox | sql | 00 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/integration/BusinessEventConsumerScheduler.java`, `source-apps/shared/common/src/main/java/com/mfg/common/integration/BusinessEventService.java` |
| mfg_ops.biz_outbox | sql | 00 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/integration/BusinessEventService.java` |
| mfg_ops.ops_dw_run_log | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ops.ops_ingest_run_log | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ops.ops_injection_manifest | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| mfg_ops.ops_watermark | out_of_scope | P6/P7 | P6/P7 已暂缓；仅保留原初始化表，不作为 P0–P5 实体验收对象。 依据：`docs/implementation-roadmap.md` |
| src_crm.crm_competitor | planned | 04 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_crm.crm_complaint | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/controller/CrmP2Controller.java`, `source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_contract | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/controller/CrmP2Controller.java`, `source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_contract_change | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_lead | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/controller/CrmP2Controller.java`, `source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_lead_assignment | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_lead_follow | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_md_customer | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_crm.crm_opportunity_product | planned | 04 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_crm.crm_payment_plan | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_quotation | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/controller/CrmP2Controller.java`, `source-apps/crm/src/main/java/com/mfg/crm/controller/OpportunityController.java` |
| src_crm.crm_quotation_line | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_crm.crm_sales_forecast | planned | 04 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_eam.eam_md_material | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_energy.energy_md_equipment | planned | 07 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_erp.erp_atp_snapshot | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_credit_check | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_customer_credit | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/controller/ErpP2Controller.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_invoice | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/controller/ErpP2Controller.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_md_bom | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_md_bom_line | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_md_cost_center | planned | 04 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_erp.erp_md_customer | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_erp.erp_md_material | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_erp.erp_md_product | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_erp.erp_md_supplier | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_erp.erp_mrp_requirement | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_mrp_run | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/controller/ErpP2Controller.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_order_cost | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_payable | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/controller/ErpP2Controller.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_plan_suggestion | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/controller/ErpP2Controller.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_purchase_requisition | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_receipt | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/controller/ErpP2Controller.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_receivable | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/controller/ErpP2Controller.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_sales_order_change | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_settlement | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_erp.erp_supply_snapshot | sql | 04 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_mdm.md_account_subject | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmReferenceService.java` |
| src_mdm.md_change_history | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmGovernanceService.java` |
| src_mdm.md_change_request | planned | 02 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_mdm.md_code_mapping | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmGovernanceService.java` |
| src_mdm.md_cost_center | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmReferenceService.java` |
| src_mdm.md_distribution_log | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/DistributionMonitorService.java`, `source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_mdm.md_employee | planned | 02 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_mdm.md_inbox | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmOutboxService.java` |
| src_mdm.md_merge_record | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmGovernanceService.java` |
| src_mdm.md_org_unit | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmReferenceService.java` |
| src_mdm.md_outbox | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmOutboxService.java` |
| src_mdm.md_partner_contact | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmGovernanceService.java`, `source-apps/crm/src/main/java/com/mfg/crm/service/CrmP2Service.java` |
| src_mdm.md_production_version | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmGovernanceService.java`, `source-apps/erp/src/main/java/com/mfg/erp/service/ErpP2Service.java` |
| src_mdm.md_reconciliation | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmOutboxService.java` |
| src_mdm.md_unit | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmReferenceService.java` |
| src_mdm.md_warehouse | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmGovernanceService.java` |
| src_mdm.md_work_center | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MdmGovernanceService.java` |
| src_mes.mes_md_material | sql | 06 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_mes.mes_md_routing_operation | sql | 06 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_plm.plm_document | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/service/PlmCatalogService.java` |
| src_plm.plm_eco | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/service/EngineeringChangeChainService.java` |
| src_plm.plm_ecr | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/service/EngineeringChangeChainService.java` |
| src_plm.plm_engineering_baseline | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/service/PlmCatalogService.java` |
| src_plm.plm_impact_analysis | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/service/EngineeringChangeChainService.java` |
| src_plm.plm_md_bom | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/controller/PlmStructureController.java`, `source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_plm.plm_md_bom_line | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/controller/PlmStructureController.java`, `source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_plm.plm_md_material | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_plm.plm_md_product | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_plm.plm_md_routing | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_plm.plm_md_routing_operation | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_plm.plm_product_family | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/service/PlmCatalogService.java` |
| src_plm.plm_product_version | sql | 02 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/plm/src/main/java/com/mfg/plm/service/PlmCatalogService.java` |
| src_qms.qms_capa | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_qms.qms_eight_d | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_qms.qms_md_material | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_qms.qms_ncr | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java`, `source-apps/shared/common/src/main/java/com/mfg/common/service/P3FlowService.java` |
| src_qms.qms_sampling_plan | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_qms.qms_standard | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_qms.qms_standard_item | planned | 03 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_srm.srm_asn | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java`, `source-apps/shared/common/src/main/java/com/mfg/common/service/P3FlowService.java` |
| src_srm.srm_md_material | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_srm.srm_md_supplier | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_srm.srm_onboarding | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_srm.srm_qualification | planned | 05 | 已建表但未找到业务访问实现；保留为待实施缺口，不用登记掩盖未完成状态。 依据：`docs/plans/README.md` |
| src_srm.srm_rfq | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java`, `source-apps/shared/common/src/main/java/com/mfg/common/service/P3FlowService.java` |
| src_srm.srm_supplier_performance | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_srm.srm_supplier_quality | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java`, `source-apps/shared/common/src/main/java/com/mfg/common/service/P3FlowService.java` |
| src_srm.srm_supplier_quote | sql | 05 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java`, `source-apps/shared/common/src/main/java/com/mfg/common/service/P3FlowService.java` |
| src_wms.wms_count_line | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/wms/src/main/java/com/mfg/wms/service/InventoryTransactionService.java` |
| src_wms.wms_count_plan | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/wms/src/main/java/com/mfg/wms/controller/WmsP3Controller.java`, `source-apps/wms/src/main/java/com/mfg/wms/service/InventoryTransactionService.java` |
| src_wms.wms_inventory_action | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_wms.wms_inventory_ledger | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/wms/src/main/java/com/mfg/wms/service/InventoryTransactionService.java` |
| src_wms.wms_md_material | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/mdm/src/main/java/com/mfg/mdm/service/MasterDataDistributor.java` |
| src_wms.wms_putaway | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java` |
| src_wms.wms_receipt | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/shared/common/src/main/java/com/mfg/common/service/P3CrudService.java`, `source-apps/shared/common/src/main/java/com/mfg/common/service/P3FlowService.java` |
| src_wms.wms_transfer | sql | 03 | 已有 JDBC 或只读副本访问路径，保留 SQL 管理；列集合受本登记与实时 schema 检查约束。 依据：`source-apps/wms/src/main/java/com/mfg/wms/controller/WmsP3Controller.java`, `source-apps/wms/src/main/java/com/mfg/wms/service/InventoryTransactionService.java` |
