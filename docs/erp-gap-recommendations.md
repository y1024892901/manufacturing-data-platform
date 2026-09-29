# ERP 常见功能缺口与建设建议

本文按仓库当前可见的 ERP 页面目录、ERP 前端视图和后端控制器盘点，用于下一阶段范围排序。CRM、SRM、WMS、EAM 等独立系统已有自己的菜单，因此下表仅列 ERP 主账和端到端经营流程中仍建议补强的能力。

盘点入口：[系统菜单目录](../apps/web-portal/src/shared/systemCatalog.ts)、[ERP 页面](../apps/web-portal/src/modules/erp/)、[ERP 后端](../source-apps/erp/src/main/java/com/mfg/erp/)、[系统功能目录](system-functional-catalog.md)。当前 ERP 菜单已覆盖销售订单、信用、ATP、MRP、计划建议、生产订单、应收、销售发票、回款、应付、财务凭证和成本毛利；其他主数据、采购协同、仓储执行、设备维护等分别位于独立系统。

## 建议优先级

| 优先级 | 建议补齐的功能 | 与现有能力的边界 | 建设理由 |
|---|---|---|---|
| P0 | ERP 采购与应付闭环：采购申请、预算/权限审批、采购订单、收货与退货、供应商发票三单匹配、付款申请 | SRM 已有询报价、供应商协同和采购订单菜单；ERP 仍需要承担企业内部请购审批、入账控制和采购到付款闭环 | 把 MRP 建议变成可审批的采购需求，并将 WMS 收货、SRM 订单、ERP 应付对账连起来；避免 SRM 订单与财务应付脱节 |
| P0 | 总账和期间结账：科目余额、凭证过账、会计期间、自动转账、关账检查、结账后调整、审计追溯 | 当前有财务凭证页面，但菜单没有独立总账、科目余额或关账模块 | 应收、应付、库存和成本需要最终汇总到总账，形成可对账的月末财务结果 |
| P1 | 库存财务与制造成本：库存计价、收发存金额、生产领料/完工成本、在制品、标准成本与实际成本差异 | WMS 管实物收发与批次；ERP 已有“成本毛利”页面，但建议扩展为可追溯的成本核算和库存估值 | 将 WMS 数量流水与财务金额连接，并把 MES 报工、物料消耗、QMS 报废返工和能源分摊计入订单成本 |
| P1 | 固定资产财务：资产卡片、在建工程转固、折旧方法与期间、资产变更、调拨、减值和处置 | EAM 管设备台账、点检与维修；ERP 的固定资产模块负责资本化、折旧与财务入账 | 同一设备需要把维护履历和财务账面价值关联起来，避免把 EAM 设备台账当成固定资产子账 |
| P1 | 现金与资金管理：银行账户、付款批次、银行流水导入、自动/人工对账、现金预测、资金日报 | 已有回款和应付模块，但不等同于银行与资金头寸管理 | 提高回款、付款、凭证和银行余额之间的可核对性 |
| P2 | 预算与滚动预测：部门/成本中心预算、订单与项目预算、预算占用、实际对比、滚动预测 | 当前有生产计划建议和成本毛利，不代表财务预算或预算控制 | 让采购申请、生产成本和资本支出在发生前经过资金与责任中心控制 |
| P2 | 税务与费用：进销项发票、税码、税额校验、费用报销、差旅标准、报销到应付/凭证 | 销售发票和应付已存在，但不能据此认定税务申报、员工费用和合规核验已完整 | 中国制造企业通常需要把发票、费用、付款与会计凭证关联并留存审计依据；具体税务接口按企业所在地及监管要求另行确定 |
| P3 | 人力与工时成本集成：员工档案、班组/岗位、排班考勤、薪资接口、MES 报工工时到成本中心 | MES 有生产执行人员/报工场景，但项目当前未见 HCM/薪资系统 | 若企业已有 HR 系统，优先建设接口；只有确认没有 HR 数据源时才新建 HCM 子系统 |
| P3 | 项目会计：项目预算、里程碑、采购承诺、项目工时、在制/交付成本、项目毛利 | CRM 合同和 ERP 销售订单不能替代项目执行与项目成本核算 | 对非标设备、工程交付或按项目生产的企业收益更高，离散重复制造企业可后置 |

## 推荐落地顺序

1. 先把 ERP 采购申请—SRM 订单—WMS 收货—应付发票三单匹配串成单据闭环，同时建立端到端单号和异常对账。
2. 再完成总账、会计期间、结账检查与自动凭证，让应收/应付/库存/制造成本进入同一财务口径。
3. 随后建设库存估值与制造成本核算，并将 MES 产量、物料批次、QMS 不合格和 EAM 停机等实际数据纳入成本差异分析。
4. 按企业规模与会计要求决定固定资产、资金、预算、费用税务、人力和项目会计的建设范围；有现成系统的优先通过受控接口集成。

## 参考资料

- Oracle Fusion Cloud 官方产品文档按 ERP 列出 Financials、Accounting Hub、Procurement、Project Management、Risk Management、ERP Analytics；SCM 又列出 Inventory Management、Manufacturing、Maintenance、Order Management、Procurement 和 PLM：[Oracle Fusion Cloud Applications 文档目录](https://docs.oracle.com/en/cloud/saas/)。
- SAP 官方 ERP 说明把核心流程概括为 finance、HR、manufacturing、supply chain、sales、procurement，并明确采购从 requisition 到 payment：[What is ERP? — SAP](https://www.sap.com/uk/products/erp/what-is-erp.html)。
- SAP S/4HANA Cloud Public Edition 能力目录列出 finance、treasury、asset management、sourcing and procurement、manufacturing、warehouse management、transportation management 和 project management：[SAP S/4HANA Cloud 能力目录](https://learning.sap.com/products/s4hana-cloud)。

以上为按本仓库现状与主流 ERP 产品能力作出的推断性建议，不意味着每家制造企业都要一次性建设全部模块。优先实施依赖企业业务规模、当前 HR/税务/银行系统，以及财务和审计要求。
