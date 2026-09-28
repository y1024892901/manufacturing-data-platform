# CRM 补充字段来源

以下可选字段参考主流 CRM 对线索/联系人、商机、报价和合同的常用记录属性，再结合制造业销售与质量协同场景筛选。字段均为可选，用于补充拜访上下文、销售预测、交付与续约条款、客诉处理期望。

- 线索联系方式、职位、行业、预算、联系偏好：HubSpot 默认联系人属性包含电子邮件、行业、职务、电话和互动信息。[HubSpot 默认联系人属性](https://knowledge.hubspot.com/properties/hubspots-default-contact-properties)
- 商机的来源、阶段、预计金额/关闭日期、概率、下一步、优先级与预测分类：HubSpot 与 Salesforce 的商机/Deal 字段资料都包含这些销售推进和预测信息。[HubSpot 默认 Deal 属性](https://knowledge.hubspot.com/properties/hubspots-default-deal-properties)；[Salesforce Opportunity 字段](https://help.salesforce.com/s/articleView?id=sf.opp_fields.htm&language=en_US&type=5)
- 报价的付款/交付条件、有效期、收货地址：Salesforce CPQ 报价字段文档列出有效期、付款条款和 Ship To 地址。[Salesforce CPQ Quote 字段](https://help.salesforce.com/s/articleView?id=sales.cpq_quote_fields.htm&language=zh_TW&type=5)
- 合同的起止时间、续约与续约通知期、特殊条款：Salesforce 合同布局资料包含合同起止日期、续约与地址信息。[Salesforce CPQ Contract 布局](https://help.salesforce.com/s/articleView?id=sf.cpq_contract_layout.htm&language=en_US&type=5)
- 客诉借鉴 CRM Ticket 的描述、优先级、来源、服务时限和解决方案字段，并保留与订单、交付、批次及 QMS 的制造业关联。[HubSpot CRM 记录摘要字段](https://knowledge.hubspot.com/records/summarize-records)

本项目使用本地业务命名与中文表单标签；新增列允许 NULL 或提供兼容默认值，不强制要求用户填写。
