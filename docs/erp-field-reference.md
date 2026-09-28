# ERP 表单可选字段来源

可选字段参考 SAP 与 Microsoft Dynamics 365 官方 ERP 操作资料，并按本项目现有销售、生产、计划和财务数据表选择。补充字段允许为空；单据编号、客户或物料、数量、金额等业务必填项仍按原校验执行。

| 页面 | 补充的可选字段 | 依据 |
|---|---|---|
| 销售订单 | 客户采购参考号、付款条件、交付条件、客户物料号、行备注 | Dynamics 365 的销售订单示例将请求交货日期、收货地址和请购编号放在订单信息中，并标明请购编号为非必填；[客户门户订单字段](https://learn.microsoft.com/en-us/dynamics365/supply-chain/sales-marketing/customer-portal-customize) |
| 生产订单 | 生产优先级、工厂、车间、成本中心、计划备注 | SAP 生产订单界面提供优先级与排程信息；Dynamics 365 的生产订单生命周期包含生产数量、计划完工日期、物料与工艺路线；[SAP 生产订单指南](https://help.sap.com/docs/SAP_BUSINESS_BYDESIGN/2754875d2d2a403f95e58a41a9c7d6de/2ddeddbe722d1014b9c7ce09018e9332.html)、[Dynamics 365 生产订单生命周期](https://learn.microsoft.com/en-us/dynamics365/supply-chain/production-control/create-production-orders) |
| 物料需求计划 | 计划员、运行备注 | Dynamics 365 计划资料以计划日期、需求日期和工厂/物料供需为运行与分析上下文；[生产计划](https://learn.microsoft.com/en-us/dynamics365/supply-chain/master-planning/planning-optimization/production-planning) |
| 计划建议 | 优先级、计划备注 | Microsoft Dynamics 365 的优先级计划将计划优先级用于计划订单、供应行和需求行；本页采用易录入的分档值，并允许补充处理说明；[优先级计划](https://learn.microsoft.com/en-us/dynamics365/supply-chain/master-planning/planning-optimization/priority-based-planning) |
| 销售发票 / 应收 | 客户参考号、付款条件、到期日、外部参考、备注 | Dynamics 365 说明付款条件用于计算付款到期日，并允许在销售/采购单据上单独调整；[客户付款条件](https://learn.microsoft.com/en-us/dynamics365/finance/general-ledger/tasks/establish-customer-payment-terms) |
| 回款 | 付款方式、入账账户、汇款参考、备注 | Dynamics 365 回款记录包含付款参考；付款方式可关联银行账户并帮助对账；[客户付款登记](https://learn.microsoft.com/en-us/dynamics365/finance/cash-bank-management/tasks/customer-payment-overview)、[客户付款方式](https://learn.microsoft.com/en-us/dynamics365/finance/accounts-receivable/tasks/establish-customer-method-payment) |
| 应付账款 | 供应商发票号、发票日期、收票日期、付款条件、付款方式、成本中心、备注 | Dynamics 365 供应商发票页列示收票日期、发票日期、过账日期和到期日；到期日依据付款条件计算；[供应商发票日期](https://learn.microsoft.com/en-us/dynamics365/finance/accounts-payable/vendor-invoice-dates) |
| 财务凭证 | 附件链接、业务来源及摘要 | SAP Journal Entry 字段参考将公司、过账日期列为必填，并将参考单据与凭证抬头文本列为选填；本页复用来源单号和摘要承载参考值，并补充附件链接作为审计材料入口；[SAP JournalEntry 字段参考](https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE/3ab6e6fc510f4840a5508e126ef01e22/f86c6ac195894569a9647e0ccc367a21.html) |

所有新增业务字段采用中文表单标签与表格列名；后端对未填写的可选字段保留 `NULL`，不以空值替代借贷平衡、状态流转或必填字段校验。
