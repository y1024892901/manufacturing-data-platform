# EAM 字段设计依据与系统缺口

字段设计参照设备资产与工单管理的公开产品资料。四类业务页均保留单号、设备或业务描述等核心字段，并提供可留空的扩展字段；页面标签、状态与选项用中文展示，接口仍使用内部状态码。外部字段只作为制造场景的设计参考，不代表当前系统已经实现参考产品的全部功能。

## 设备台账

设备页的必填项为设备编码、名称、类型和所属车间。型号、工作中心、安装位置、每小时产能、健康等级、购置日期与金额、质保到期日、保养周期和上次保养日期是选填项。IBM Maximo 的资产资料列出资产名称/描述、位置、类型、状态、序列号、优先级、父子资产和工单历史；资产导入指引也将设备编码列为必填，并列出位置、资产类型、状态、序列号及父资产等字段。这支持保留扩展资产信息的设计。

当前结构尚无序列号、优先级、父设备、站点、附件/图片和设备层级字段；它们是后续可选增强项，不要把它们误认为当前页面已录入的字段。

- [IBM Maximo：资产资料](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=locations-assets)
- [IBM Maximo：资产导入字段说明](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=assets-asset-import-guidelines)
- [IBM Maximo：资产管理概览与层级、计量和规格](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=assets-overview)

## 故障单

故障页必填故障单号、设备编码和故障类型；故障等级、故障描述、故障原因、关联 MES 工单、受影响生产订单和工序编码为选填项。设备名称、故障发生时间、上报人和状态由服务端关联设备或当前登录人记录，避免用户手工填写系统事实。

IBM Maximo 工单支持记录资产和位置、问题/故障信息、负责人、计划时间、相关记录以及维修资源，作为故障关联生产上下文和故障原因扩展字段的参考。

- [IBM Maximo：工单管理](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=manage-work-orders-module)
- [IBM Maximo：工单管理中的责任人、时间计划、故障和相关记录](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=work-order-management)

## 维修单

维修页必填维修单号、关联故障单号、设备编码和维修内容；维修类型、更换备件、维修成本、维修工时和备注为选填项。维修人、开工时间、完工时间和停机时长由服务端根据当前用户和维修动作记录。新建与修改接口均拒绝负成本或负工时；同一故障只允许存在一张进行中或待备件维修单。

IBM Maximo 将工单实际工时、材料、工具和服务作为实际资源，并支持查看及汇总工单成本；当前页面以备件文本、工时和成本字段保留轻量记录，尚未实现明细领料、工具、服务资源或资产层级成本归集。

- [IBM Maximo：工单实际工时、材料、服务和工具](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=overview-actuals-work-orders)
- [IBM Maximo：工单成本与资产层级成本汇总](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=orders-costs-tickets-work)

## 点检单

点检页必填点检单号、设备编码、周期类型和计划日期；点检项目 JSON 为选填，完成时由当前用户和系统日期填入检查人、实际日期；点检结果必须在执行动作中选择，异常说明仅异常时追加且可留空。服务端校验周期、设备存在性和检查项目 JSON 格式。

IBM Maximo 预防性维护计划支持按时间、计量读数或二者组合排程，并可从维护计划生成工单。当前页面仅维护点检安排与执行结果，尚无按计划自动派生任务或计量表读数触发功能。

- [IBM Maximo：主预防性维护记录与时间/计量计划](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=pm-master-preventive-maintenance-records)
- [IBM Maximo：预防性维护计划生成工单](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=records-generating-work-orders-from-preventive-maintenance)

## 仍可补充的 EAM / ERP 能力

现有 EAM 范围是设备、故障、点检和维修；备件领用/退还与补货、点检计划自动生成任务、设备层级和位置层级、移动巡检、安全作业许可、停机率与 MTBF/MTTR 报表仍需另行建设。ERP 建议优先考虑固定资产折旧与总账集成、采购申请到收货/发票的完整闭环、预算和项目成本、人力与薪资管理。平台当前已包含 ERP、SRM、WMS、QMS、MES 等模块，扩展时应先打通跨系统单据和成本数据。

- [Oracle Fusion Cloud Applications：ERP 与供应链模块目录](https://docs.oracle.com/en/cloud/saas/index.html)
- [SAP：ERP 常见核心业务范围](https://www.sap.com/products/erp/what-is-erp.html)
- [Oracle：采购到付款与供应商管理](https://www.oracle.com/erp/finance-and-accounting/)
