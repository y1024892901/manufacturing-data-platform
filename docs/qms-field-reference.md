# QMS 字段设计与外部依据

本轮 QMS 页面仍要求核心单据号、物料/来源、数量等必填业务字段；新增的追溯、环境、责任、附件与效果验证信息均为非必填，便于先落地现有流程，再逐步补齐质量体系数据。

| 页面 | 已有/核心字段 | 新增非必填字段 | 设计依据 |
| --- | --- | --- | --- |
| 检验标准 | 标准编号、名称、检验类型、物料、版本 | 生效日期、负责人、引用文件、适用范围说明、备注 | Oracle 检验计划区分收货、资源、库存、在制品和资产等适用对象，并要求计划规格/判定准则。范围说明用于补充本厂产品、工序或场景。 |
| 抽样方案 | 批量范围、样本数、接收/拒收数、AQL | 抽样方式、检验水平、样本单位、抽样频次说明、生效日期、引用文件、负责人、备注 | Oracle 检验水平定义检验数量与频率，支持 AQL、固定数量、百分比，也记录单样本用量及计量单位。 |
| 检验单 | 检验类型、来源、物料、批次、送检/合格/不合格数、判定 | 抽样数量、检验依据、检验方法、检验位置或工位、设备编号、环境温度/湿度、备注 | SAP 检验批按工序/检验点和特性记录结果，抽样及判定依据可追溯到检验计划。 |
| 不合格品记录 | 不合格单号、检验单、数量、缺陷类型/等级、处置 | 缺陷代码、遏制措施、发生位置、备注 | SAP 支持在检验目录中维护预定义缺陷代码，并把缺陷记录关联检验结果，便于按代码复用与分析。 |
| NCR 评审 | NCR 编号、检验单、来源、数量、严重度、处置状态 | 缺陷描述、遏制措施、根本原因、风险评估说明、责任人、完成期限、备注 | ASQ 建议正式 CAPA 前按风险评估；记录评估依据帮助质量人员说明升级、遏制和处置决策。 |
| 返工作业 | 返工单、不合格品、生产订单、数量、状态/工时 | 负责人、返工方法、复验结论、复验说明、合格/报废数、备注 | SAP 结果记录允许附检验备注；本字段用于保留返工后的复验现象和测量依据。 |
| CAPA | CAPA/NCR、根因、纠正/预防措施、责任人、期限、状态 | 有效性判据、验证结果、验证说明、有效性证据或数据、备注 | ASQ 的 CAPA 闭环要求以数据核实问题确已消除并保持，证据字段可记录复验批次、抽样数或趋势结论。 |
| 供应商 8D | 8D/NCR、供应商、团队、问题、遏制/根因/措施、期限、状态 | 供应商 8D 参考号、负责人、有效性结论、验证说明、备注 | ASQ 8D 模板包含供应商 8D 编号、客户索赔编号、团队、问题描述和各阶段行动，可与内部 NCR 号并存追溯。 |

## 外部参考

- Oracle [Inspection Plans](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/26b/fauqm/inspection-plans.html)：检验计划包含要采集的检验特性、目标/限值，以及采集时间和频次。
- Oracle [Inspection Levels](https://docs.oracle.com/en/cloud/saas/supply-chain-and-manufacturing/25d/fauqm/inspection-levels.html)：检验水平可定义全检、抽样方式，支持 AQL、固定数量或百分比抽样。
- SAP [Results Recording](https://help.sap.com/docs/SAP_S4HANA_ONPREMISE/2bc3ee8d1c83404e8cf62418640004f2/01c1b65334e6b54ce10000000a174cb4.html)：检验批关联规范与样本，记录特性结果和缺陷并进行接收/拒收判定。
- SAP [Inspection Lots](https://help.sap.com/docs/SAP_S4HANA_ONPREMISE/9905622a5c1f49ba84e9076fc83a9c2c/fc670f80833148e19b1a76f3e67ba4fc.html)：检验批可分配检验规范、计算样本数、记录结果/缺陷并完成使用决策。
- SAP [Defects Recording](https://help.sap.com/docs/SAP_ERP/250374f0514e4e0f9057066374265eba/34c7b65334e6b54ce10000000a174cb4.html)：缺陷可以通过检验目录中维护的预定义缺陷代码记录。
- ASQ [Eight Disciplines 8D](https://asq.org/quality-resources/eight-disciplines-8d)：说明 D1 团队、D2 问题定义、D3 遏制、D4 根因验证及后续纠正行动。
- ASQ [Corrective and Preventive Action](https://careers.asq.org/career-resources/on-the-job-3/capa-corrective-and-preventive-action-for-quality-engineer-2026-112)：CAPA 流程围绕问题描述、根因、纠正/预防措施及有效性验证。

本轮增加的字段均为可空字段，不改变既有单据建档必填规则。外部系统只用于借鉴常见业务记录项，最终是否填写、字段长度和缺陷分类仍由企业质量程序与受控检验规范确定；这里不声称这些字段本身构成认证符合性。
