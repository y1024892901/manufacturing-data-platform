# SRM 页面字段依据

本文记录 SRM 页面字段扩展的外部业务依据。SAP Help 将供应商数据分为基础资料、公司代码数据和采购组织数据，并列出地址、税号、联系方式、订单币种、付款条件、采购组和运输条件等字段；本系统将对应字段作为选填扩展，是否填写由企业采购流程决定。

## 页面字段

| 页面 | 必填字段 | 新增/补充的选填字段 | 外部依据与处理方式 |
| --- | --- | --- | --- |
| 供应商准入 | 申请编号、供应商名称 | 供应品类、联系人及电话/邮箱、统一社会信用代码、注册地址、供应商类型、付款条件、常用币种、官网、准入说明 | SAP 供应商主数据覆盖名称、地址、税务标识和联系资料；采购数据覆盖订单币种、付款条件、采购组和运输条件。供应商类型和准入说明作为本地审批补充信息。 |
| 供应商资质 | 准入申请记录、资质类别、证书编号 | 发证日期、到期日期、发证机构、附件文件名、备注 | SAP 证书问题可记录证书类型、编号、签发方、生效/到期日期、附件和描述；到期状态应由日期与审核流程管理。 |
| 询价与定标 | 询价编号、主题、物料、数量、需求日期 | 来源采购申请、报价币种、交付条款、收货地址、邀请供应商数、采购组、技术与质量要求 | SAP 采购数据使用采购组、订单币种、付款和运输条件；寻源询价保留来源需求和采购要求，供后续报价及定标追溯。 |
| 供应商报价 | 询价、供应商、未税单价、承诺交货日期 | 报价币种、付款条件/账期、税率、最小起订量、运费、有效期、质保期、文件名、报价说明 | SAP 供应商采购信息包括币种、付款条件；供应商协作采购流程支持订单/发货通知关联、运输信息和备注。价格、交期及报价期限用于横向比较。 |
| 采购订单 | 订单编号、供应商、物料、数量、单位、单价、要求交货日期 | 供应商承诺日期、关联生产订单、来源采购申请、订单币种、付款条件、运输条款、收货地址、采购员、供应商参考号、备注 | SAP 采购订单及供应商采购数据覆盖供应商、交期、付款条件、收货方/地点、币种和运输条件。订单总额由数量乘单价计算，不允许手工覆盖。 |
| ASN 到货协同 | ASN 编号、采购订单、供应商、物料、发运数量 | 批次、发运日期、预计到货日期、承运单位、物流单号、送货单号、联系人、发货说明 | SAP 采购/ASN 数据可包括发货通知号、发货日期、承运商、跟踪号和备注；ASN 必须关联已下达采购订单。 |
| 供应商质量 | 问题编号、供应商 | 来源单号、不合格数量、问题描述、整改期限、根本原因、临时遏制/纠正/预防措施、负责人、验证关闭说明 | SAP Quality Issue Resolution 支持供应商使用 8D 协作识别根因、定义预防/纠正措施并记录问题解决过程；整改与关闭字段保持可追踪。 |
| 供应商绩效 | 统计台账，无手工新增 | 此页为只读指标；当前展示供应商、统计期间、准时交付率、来料合格率、PPM、综合得分和等级 | SAP 供应商评价采用交期、价格、数量、质量、质量通知和问卷等指标；这些字段应由收货/检验/采购业务计算或汇总，故不放在手工录入表单中。 |

选填字段留空时以 `NULL` 保存，不参与必填校验。业务编号、状态、审核结论、收货数量和订单总额由系统或对应状态操作维护。

## SAP Help 参考

- [Required Organizational Units — Supplier Master Data](https://help.sap.com/docs/s4hana-best-practices/create-supplier-master-bne-56ccaf9fbaf5bc19a4cd462421191488/required-organizational-units)
- [Data Model for Supplier — Supplier Master Governance](https://help.sap.com/docs/SAP_MASTER_DATA_GOVERNANCE/f16db93627294eefac9cd74aa84445af/797e912fe1a342e1a9de5be63dab7155.html)
- [Supplier Certificate Management](https://help.sap.com/docs/strategic-sourcing/monitoring-supplier-risk/managing-supplier-certificates)
- [Purchase Order Header Data Reference](https://help.sap.com/docs/buying-invoicing/procurement-data-import-and-administration-guide/purchase-order-header-data-reference)
- [Invoices for Purchase Orders](https://help.sap.com/docs/SAP_S4HANA_ON-PREMI-SE/af9ef57f504840d2b81be8667206d485/be5eb6531de6b64ce10000000a174cb4.html)
- [Supplier Evaluation](https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE/af9ef57f504840d2b81be8667206d485/99edf736476e46c397f2529886750117.html?locale=en-USstate%3DPRODUCTION)
- [Integration with SAP Quality Issue Resolution](https://help.sap.com/docs/PRODUCT_ID/0dd6552bb884415f93aaa24c788ae644/bf31dd9840c344faab44dc40737a1f76.html)
