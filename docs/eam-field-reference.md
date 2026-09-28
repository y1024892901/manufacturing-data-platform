# EAM 字段设计依据与系统缺口

字段以设备台账、故障单、点检单和维修单为边界，保留现有数据表已支持的属性，并把可补充信息设为非必填。页面和状态使用中文显示，接口仍使用系统内部状态码。

## 设备台账

参考 IBM Maximo 设备资料和导入规范中的设备编码、名称、设备类型、运行状态、序列号、位置、优先级和父子关系。当前台账增加/展示型号、工作中心、车间、额定产能、健康等级、购置日期与金额、质保到期、保养周期和上次保养日期；采购、质保和保养信息均可不填。

- [IBM Maximo：设备资料](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=locations-assets)
- [IBM Maximo：设备导入字段说明](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=assets-asset-import-guidelines)

## 故障单与维修单

参考 IBM Maximo 工单资料中的设备/位置关联、工作类型、优先级、负责人、相关记录、工时与物料资源。故障单提供故障等级、故障原因、受影响的工序/生产订单和工单编号；维修单提供维修类型、更换备件、维修成本、维修工时和备注。除单号、设备和业务主描述外，扩展字段均可不填。

- [IBM Maximo：工单管理](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=module-work-orders-application)
- [IBM Maximo：资产与工单相关信息](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=locations-assets)

## 点检单

参考 IBM Maximo 预防性维护资料中的资产/位置、日期或计量读数触发方式、作业计划与安全信息。当前点检单包含点检周期类型、计划日期、点检项目、实际日期、检查结果、异常描述和检查人；点检项目、实际结果、异常信息及检查人都可不填，完成点检时由执行人和日期自动记录。

- [IBM Maximo：预防性维护应用](https://www.ibm.com/docs/en/mfo-and-g/cd?topic=module-preventive-maintenance-application)
- [IBM Maximo：日期与计量读数触发的维护周期](https://www.ibm.com/docs/en/masv-and-l/maximo-manage/cd?topic=records-master-pm-frequency-schedules)

## 仍可补充的 EAM / ERP 能力

现有 EAM 范围是设备、故障、点检和维修；备件领用/退还与补货、点检计划自动生成任务、设备层级和位置层级、移动巡检、安全作业许可、停机率与 MTBF/MTTR 报表仍需另行建设。ERP 建议优先考虑固定资产折旧与总账集成、采购申请到收货/发票的完整闭环、预算和项目成本、人力与薪资管理。平台当前已包含 ERP、SRM、WMS、QMS、MES 等模块，扩展时应先打通跨系统单据和成本数据。

- [Oracle Fusion Cloud Applications：ERP 与供应链模块目录](https://docs.oracle.com/en/cloud/saas/index.html)
- [SAP：ERP 常见核心业务范围](https://www.sap.com/products/erp/what-is-erp.html)
- [Oracle：采购到付款与供应商管理](https://www.oracle.com/erp/finance-and-accounting/)
