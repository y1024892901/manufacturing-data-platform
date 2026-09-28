# QMS 字段设计与外部依据

本轮 QMS 页面仍要求核心单据号、物料/来源、数量等必填业务字段；新增的追溯、环境、责任、附件与效果验证信息均为非必填，便于先落地现有流程，再逐步补齐质量体系数据。

| 页面 | 已有/核心字段 | 新增非必填字段 | 设计依据 |
| --- | --- | --- | --- |
| 检验标准 | 标准编号、名称、检验类型、物料、版本 | 生效日期、负责人、引用文件、备注 | 检验计划应包含检验特性、限值/目标及何时、如何采集；检查计划还需关联产品或适用条件。 |
| 抽样方案 | 批量范围、样本数、接收/拒收数、AQL | 抽样方式、检验水平、样本单位、生效日期、引用文件、负责人、备注 | 抽样方案常按 AQL、固定样本数或百分比定义；检验水平决定检验比例与频次。 |
| 检验单 | 检验类型、来源、物料、批次、送检/合格/不合格数、判定 | 抽样数量、检验依据、检验方法、设备编号、环境温度/湿度、备注 | 检验结果关联检验批、检验规范、特性与样本；记录结果/缺陷后再做接收或拒收判定。 |
| 不合格品记录 | 不合格单号、检验单、数量、缺陷类型/等级、处置 | 遏制措施、发生位置、备注 | 检验结果可直接记录缺陷；缺陷类型、位置和遏制信息便于追溯与围堵。 |
| NCR 评审 | NCR 编号、检验单、来源、数量、严重度、处置状态 | 缺陷描述、遏制措施、根本原因、责任人、完成期限、备注 | 不合格闭环通常需要隔离/遏制、根因、责任与期限，再记录处置决定。 |
| 返工作业 | 返工单、不合格品、生产订单、数量、状态/工时 | 负责人、返工方法、验证结论、合格/报废数、备注 | 返工需要关联来源和生产作业，并保留执行与复验结果。 |
| CAPA | CAPA/NCR、根因、纠正/预防措施、责任人、期限、状态 | 有效性判据、验证结果、验证说明、备注 | CAPA 不仅要记录根因和措施，还应核验措施效果后再关闭。 |
| 供应商 8D | 8D/NCR、供应商、团队、问题、遏制/根因/措施、期限、状态 | 负责人、有效性结论、验证说明、备注 | 供应商问题闭环需提交问题与遏制、根因、纠正/预防措施并验证有效性。 |

## 外部参考

- Oracle [Inspection Plans](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/26b/fauqm/inspection-plans.html)：检验计划包含要采集的检验特性、目标/限值，以及采集时间和频次。
- Oracle [Inspection Levels](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/25d/fauqm/inspection-levels.html)：检验水平可定义全检、抽样方式，支持 AQL、固定数量或百分比抽样。
- SAP [Results Recording](https://help.sap.com/docs/SAP_S4HANA_ONPREMISE/2bc3ee8d1c83404e8cf62418640004f2/01c1b65334e6b54ce10000000a174cb4.html)：检验批关联规范与样本，记录特性结果和缺陷并进行接收/拒收判定。
- SAP [Inspection Lots](https://help.sap.com/docs/SAP_S4HANA_ONPREMISE/9905622a5c1f49ba84e9076fc83a9c2c/fc670f80833148e19b1a76f3e67ba4fc.html)：检验批可分配检验规范、计算样本数、记录结果/缺陷并完成使用决策。
- ASQ [Corrective and Preventive Action](https://careers.asq.org/career-resources/on-the-job-3/capa-corrective-and-preventive-action-for-quality-engineer-2026-112)：CAPA 流程围绕问题描述、根因、纠正/预防措施及有效性验证。
