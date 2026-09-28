# MES 字段依据与功能核对

本文记录 MES 页面字段与外部制造系统资料的对应关系，以及本次完成的单据范围。表单中标注“选填”的内容可以留空；关键关联号、工序、数量和异常描述仍由前后端校验。

## 页面与字段

| 页面 | 必填字段 | 选填字段与用途 | 外部依据 |
|---|---|---|---|
| 生产工单 | 工单号、ERP 生产订单号、产品编码、工序序号/编码/名称、计划数量 | 工艺路线及版本、工作中心、车间、设备、班组负责人、计划开始/结束时间、计划工时、优先级、生产批次、班次、补充说明 | [Odoo 制造工单与工序](https://www.odoo.com/documentation/master/applications/inventory_and_mrp/manufacturing/basic_setup/manufacturing_work_orders.html)包含生产订单、产品、数量、工作中心和工序；批次、班次及现场备注作为可选补充。 |
| 派工排产 | 沿用工单的必填关联和计划数量 | 车间、负责人、工作中心、设备、班次、计划时段、工时、优先级、批次、说明 | [Odoo 工作中心](https://www.odoo.com/documentation/16.0/applications/inventory_and_mrp/manufacturing/management/using_work_centers.html)将工作中心与产能、效率、负荷及时间关联；[Odoo 车间执行](https://www.odoo.com/documentation/18.0/applications/inventory_and_mrp/manufacturing/shop_floor/shop_floor_overview.html)以工单、产品、数量和状态组织现场执行。 |
| 报工明细 | 报工单号、工单号、合格数量 | 报废数量、报工日期、班次、开始/结束时间、工时、设备、异常暂停、停机原因/时长、补充说明；操作人自动记录 | [SAP 生产订单确认](https://help.sap.com/docs/SAP_S4HANA_ON-PREMISE/25a41481f62e469ba0e61015a0d39d20/2d6b6b54cd410e4ee10000000a423f68.html)围绕订单/工序确认生产数量和作业数据；本页面额外记录现场班次、设备与停机原因。 |
| Andon 异常 | 异常单号、异常类型、等级、异常描述 | 关联工单/生产订单、设备、车间、影响数量、停线时长、期望响应时间；响应人/时间、响应说明、处理方案与结果在流程中登记 | [Odoo 车间执行](https://www.odoo.com/documentation/18.0/applications/inventory_and_mrp/manufacturing/shop_floor/shop_floor_overview.html)提供现场工单、工作中心和执行状态上下文；异常影响量和响应闭环字段是结合 MES 现场管理补充的可选信息。 |
| MES 首页 | 无录入字段 | 展示工单状态、计划与报工进度，并提供工单、派工、报工、Andon 快捷入口 | 首页摘要读取 MES 工单；各单据页的字段来源见上表。 |

## 单据规则

- 工单仅允许在未开工或已下达时修改；开始执行后不能删除。删除前会检查是否已有报工明细。
- 新报工只能用于已开工工单。合格数与报废数之和必须大于 0，累计报工不得超过工单计划数量。
- 创建、修改和删除报工都会在同一事务内重算工单完工数、合格数、报废数和实际工时；报工完成或撤销后工单状态随汇总数量更新。
- Andon 处理流程为“待响应 → 已响应 → 已处理 → 已关闭”。已响应单据不能删除，已处理后才能关闭。
- 工单、报工和 Andon 的列表、详情、修改、删除均按权限和数据范围校验。接口失败由全局请求拦截器直接弹出中文错误对话框。

## ERP 后续功能建议

仓库当前已包含销售订单、信用检查、ATP 交期承诺、MRP 运行/计划建议、生产订单、应收、销售发票、回款核销、应付、财务凭证和订单成本毛利。建议先扩展这些已有链路，而不是重复建设。

| 建议补齐 | 当前仓库可见能力 | 建议范围 |
|---|---|---|
| 总账与期间结账 | 有会计科目主数据和可借贷平衡、可过账的财务凭证；ERP 页面有凭证和单订单毛利 | 增加总账分录/科目余额、试算平衡、期间控制、结账任务与资产负债表/利润表。Dynamics 365 的期间结账流程还包含检查应收应付和库存、汇率重估、分摊、合并及期间财务报表。[总账期间结账](https://learn.microsoft.com/en-us/dynamics365/finance/general-ledger/close-general-ledger-at-period-end) |
| 预算编制与预算控制 | ERP 目录提到预算执行，但当前 ERP 页面/API 目录未看到预算编制、预算台账或采购预算拦截入口 | 按公司、部门、成本中心和科目编制预算，支持调拨/修订、预算与实际对比，并在采购申请、采购单和费用凭证上执行超预算预警或拦截。[预算控制](https://learn.microsoft.com/en-us/dynamics365/finance/budgeting/budget-control-overview-configuration) |
| 供应商付款与银行对账 | 已有应付记录创建/维护能力；未看到付款提案、付款日记账、银行流水导入与自动匹配入口 | 从到期应付生成付款计划/付款批次，审批并登记付款，导入银行对账单后自动匹配回款、付款和银行手续费。[供应商付款提案](https://learn.microsoft.com/en-us/dynamics365/finance/accounts-payable/create-vendor-payments-payment-proposal)、[高级银行对账](https://learn.microsoft.com/en-us/dynamics365/finance/cash-bank-management/advanced-bank-reconciliation-overview) |
| 生产订单成本与在制品 | 有按销售订单汇总的成本毛利查询；MES 现有报工可以提供数量和工时来源 | 将物料实际消耗、工序工时、间接费用和完工数量归集到生产订单，提供在制品、标准/实际成本比较及数量/价格/替代差异。[生产订单成本分析](https://learn.microsoft.com/en-us/dynamics365/supply-chain/cost-management/production-order-cost-analysis) |
| 财务固定资产 | EAM 已管理设备、故障、维修等实物信息 | 增加资产卡片、购置/转固、资产类别与账簿、折旧规则/批处理、转移处置、折旧凭证，并与 EAM 设备编码关联。[固定资产折旧方法](https://learn.microsoft.com/en-us/dynamics365/finance/fixed-assets/fixed-asset-depreciation-conventions) |
| 税务与电子发票 | 已有销售发票记录和“开票”流程 | 增加税号/税率校验、销项/进项税额处理、电子发票平台开具/验真/红冲与归档，按实际经营地法规接入。 |
| 人力、考勤与薪资 | 目前 MES 仅保存操作人账号，系统目录没有 HR/Payroll 页面 | 建立员工与组织档案、班次/工时/考勤、工资计算和成本中心分摊；优先让 MES 报工工时可复用，避免重复录入。 |

建议顺序：总账与结账、付款与银行对账、生产实际成本与在制品、预算控制、固定资产、税务/电子发票、人力与薪资。实际优先级应结合企业当前会计制度、银行/税务接口和合规地区确认。
