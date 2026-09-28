const labels:Record<string,string>={
  DRAFT:'草稿',PENDING:'待审批',PROCESSING:'处理中',APPROVING:'审批中',APPROVED:'已通过',PUBLISHED:'已发布',RELEASED:'已发布',REJECTED:'已驳回',FAILED:'失败',SUCCESS:'成功',RETRYING:'重试中',DEAD:'待人工处理',COMPLETED:'已完成',RUNNING:'进行中',CANCELLED:'已撤回',ACTIVE:'启用',ENABLED:'启用',DISABLED:'停用',INACTIVE:'停用',LOCKED:'已锁定',CHANGING:'变更中',
  DESIGN:'设计阶段',TRIAL:'试制阶段',MASS:'量产准备',PROD:'量产阶段',EOL:'停止生产',NORMAL:'普通',HIGH:'高',MEDIUM:'中',LOW:'低',URGENT:'紧急',PUBLIC:'公开',INTERNAL:'内部',CONFIDENTIAL:'机密',DRAWING:'图纸',PROCESS_CARD:'工艺卡',WORK_INSTRUCTION:'作业指导书',SPECIFICATION:'技术规范',
  CUSTOMER:'客户',SUPPLIER:'供应商',MATERIAL:'物料',PRODUCT:'产品',BOM:'物料清单',ROUTING:'工艺路线',CREATE:'新增',UPDATE:'修改',DELETE:'删除',APPROVE:'同意',REJECT:'驳回',SUBMIT:'提交',TRANSFER:'转交',ADD_SIGN:'加签',COPY:'抄送',CANCEL:'撤回',COMPLETE:'完成',SYSTEM:'系统',EFFECTIVE:'已生效',EXPIRED:'已失效',WON:'赢单',LOST:'丢单',QUALIFY:'需求确认',PROPOSAL:'方案报价',NEGOTIATE:'商务谈判',PASSED:'已通过',EXCEPTION:'例外',UNCHECKED:'未检查',COMMITTED:'已承诺',PARTIAL:'部分满足',OPEN:'待处理',ISSUED:'已开票',UNAPPLIED:'未核销',APPLIED:'已核销',PROPOSED:'建议中',CONFIRMED:'已确认',
  FOLLOWING:'跟进中',CONVERTED:'已转商机',INVALID:'已失效',VOID:'已作废',TERMINATED:'已终止',IN_PROGRESS:'处理中',PIPELINE:'销售机会',BEST_CASE:'最佳情景',COMMIT:'承诺成交',OMIT:'不纳入预测',CALL:'电话',VISIT:'拜访',EMAIL:'邮件',MEETING:'会议',WEB:'官网',REFERRAL:'客户转介绍',EXHIBITION:'展会',PHONE:'电话开发',PARTNER:'合作伙伴',OTHER:'其他',CNY:'人民币',USD:'美元',EUR:'欧元',NEW_BUSINESS:'新客户业务',EXISTING_BUSINESS:'存量客户业务',YES:'是',NO:'否',DIRECT:'直客',DEALER:'经销商',AGENT:'代理商',A:'A级',B:'B级',C:'C级',D:'D级',OVERDUE:'已逾期',BAD_DEBT:'坏账',SETTLED:'已结清',RAW:'原材料',SEMI:'半成品',FINISHED:'成品',CONSUMABLE:'耗材',SERVICE:'服务类',OUTSOURCE:'委外加工',VALID:'资质有效',QUALIFIED:'已合格',UNQUALIFIED:'未合格',REVIEW:'复核中',EXPIRED_SOON:'即将到期',QUANTITY:'数量单位',WEIGHT:'重量单位',LENGTH:'长度单位',AREA:'面积单位',TIME:'时间单位',COMPANY:'公司',FACTORY:'工厂',WORKSHOP:'车间',LINE:'产线',PRODUCTION:'生产',ADMIN:'行政',ASSET:'资产',LIABILITY:'负债',EQUITY:'所有者权益',COST:'成本',REVENUE:'收入',CONSIGN:'寄售仓',GENERAL:'普通仓',SUBCONTRACT:'委外',BYPRODUCT:'副产品',CO_PRODUCT:'联产品',CURRENT:'当前版本',SUPERSEDED:'已被替代',MERGED:'已合并',MAPPED:'已映射',RENAME:'编码变更',MERGE:'合并',RESTORE:'恢复',REASSIGN:'重新分配'
}
Object.assign(labels, { SPARE:'备件', PACKAGING:'包装材料', EBOM:'工程BOM', MBOM:'制造BOM', FG:'成品', PURCHASE:'采购件', EXPIRING:'即将到期', NET30:'30天账期', NET60:'60天账期', PREPAID:'预付款', PREPAY:'预付款', MATERIAL_CATEGORY:'物料分类', UNIT:'计量单位', ORG_UNIT:'组织单元', COST_CENTER:'成本中心', ACCOUNT_SUBJECT:'会计科目', EMPLOYEE:'员工', WAREHOUSE:'仓库', WORK_CENTER:'工作中心', PRODUCTION_VERSION:'生产版本', DISABLE:'停用', PUBLISH:'发布' })
Object.assign(labels, {
  CERTIFIED:'已认证', BLACKLISTED:'已列入黑名单', QUOTING:'报价中', AWARDED:'已定标', SUBMITTED:'已提交',
  SHIPPED:'已发运', ARRIVED:'已到货', RECEIVED:'已收货', RETURNED:'已退回', EIGHT_D_REQUIRED:'待提交8D',
  VERIFYING:'验证中', CREATED:'新建', SENT:'已下达', PENDING_INSPECTION:'待检', PARTIAL:'部分完成',
  SUPPLIER:'供应商', RFQ:'询价单', ASN:'发货通知', EIGHT_D:'8D整改', PARTIAL_RECEIPT:'部分收货'
})
Object.assign(labels, { ELECTRIC:'电力', WATER:'水', GAS:'天然气', STEAM:'蒸汽', COMPRESSED_AIR:'压缩空气', KWH:'千瓦时', M3:'立方米', TON:'吨', GJ:'吉焦', MWH:'兆瓦时', L:'升', MAIN:'总表', SUBMETER:'分表', MAINTENANCE:'校准维护', IGNORED:'已忽略', CRITICAL:'严重', EQUIPMENT:'设备', METER:'仪表' })
Object.assign(labels, {
  AVAILABLE:'可用', FROZEN:'已冻结', QUARANTINED:'已隔离', REWORK:'返工中', SCRAPPED:'已报废',
  FREEZE:'冻结', UNFREEZE:'解冻', QUARANTINE:'隔离', RELEASE:'质量放行', SCRAP:'报废', SUPPLIER_RETURN:'退供',
  COUNT_GAIN:'盘盈', COUNT_LOSS:'盘亏', PUTAWAY:'上架', RECEIPT:'收货入库', ISSUE:'领料出库', RETURN_MATERIAL:'退料入库',
  FULL:'全库', ZONE:'库区', CYCLE:'循环盘点', GENERAL:'普通货位', RECEIVING:'收货区', PICKING:'拣选位', BULK:'存储区', SHIPPING:'发货区',
  RETURN:'退货区', WMS:'仓储系统'
})
const crmValueLabels:Record<string,string>={
  NEW:'新建',LEAD:'初步线索',QUOTATION:'报价审批',QUOTATION_RISK:'报价风险审批',CONTRACT:'合同审批',
  CANCELED:'已取消',CLOSED:'已关闭',IN_PROGRESS:'处理中',FOLLOWING:'跟进中',
  PARTNER:'合作伙伴',EXHIBITION:'展会',WEBSITE:'官网',ONLINE:'线上渠道',CAMPAIGN:'市场活动',
  PLANNED:'计划中',DELIVERED:'已交付',RENEWED:'已续签',DUE:'待处理',PAID:'已支付',
  PCS:'件',PC:'件',EA:'个',SET:'套',BOX:'箱',DAY:'天',MONTH:'月',
  CASH:'现金',WIRE:'银行转账',BANK_TRANSFER:'银行转账',CREDIT:'账期',
  NORMAL:'普通',HIGH:'高',MEDIUM:'中',LOW:'低',CRITICAL:'严重',MINOR:'轻微',MAJOR:'重大',
  YES:'是',NO:'否',TRUE:'是',FALSE:'否',
  EMAIL:'邮件',CALL:'电话',VISIT:'拜访',MEETING:'会议',WEB:'官网',PHONE:'电话',REFERRAL:'客户转介绍',OTHER:'其他'
}
const erpValueLabels:Record<string,string>={
  CREDIT_PASSED:'信用通过',CREDIT_EXCEPTION:'信用例外',ATP_COMMITTED:'交期已承诺',IN_PROD:'生产中',DELIVERED:'已交付',
  CLOSED:'已关闭',CANCELED:'已取消',CANCELLED:'已取消',FULL:'完全满足',SHORTAGE:'物料不足',READY:'已齐套',
  NOT_REQUIRED:'无需审批',ISSUED:'已开具',UNAPPLIED:'未核销',PARTIAL:'部分处理',POSTED:'已过账',
  MANUAL:'手工录入',ORDER:'按订单计划',PLANT:'按工厂计划',PURCHASE:'采购建议',PRODUCTION:'生产建议',
  HIGH:'高',NORMAL:'普通',LOW:'低',URGENT:'紧急'
}
const erpKeyLabels:Record<string,string>={
  company_code:'公司代码',companyCode:'公司代码',fiscal_period:'会计期间',fiscalPeriod:'会计期间',voucher_date:'凭证日期',voucherDate:'凭证日期',
  order_status:'订单状态',orderStatus:'订单状态',credit_status:'信用状态',creditStatus:'信用状态',atp_status:'交期承诺状态',atpStatus:'交期承诺状态',
  kit_status:'齐套状态',kitStatus:'齐套状态',run_type:'计划类型',runType:'计划类型',plan_date:'计划日期',planDate:'计划日期',
  started_at:'开始时间',startedAt:'开始时间',finished_at:'完成时间',finishedAt:'完成时间',planner:'计划员',planner_note:'计划备注',plannerNote:'计划备注',
  customer_reference:'客户采购参考号',customerReference:'客户采购参考号',shipping_terms:'交付条件',shippingTerms:'交付条件',
  customer_material_code:'客户物料号',customerMaterialCode:'客户物料号',line_remark:'行备注',lineRemark:'行备注',
  parent_material_code:'上级物料编码',parentMaterialCode:'上级物料编码',material_type:'物料类型',materialType:'物料类型',
  gross_requirement:'毛需求量',grossRequirement:'毛需求量',available_qty:'可用数量',availableQty:'可用数量',incoming_qty:'在途数量',incomingQty:'在途数量',
  safety_stock:'安全库存',net_requirement:'净需求量',netRequirement:'净需求量',required_date:'需求日期',requiredDate:'需求日期',
  shortage_flag:'是否短缺',shortageFlag:'是否短缺',suggestion_type:'建议类型',suggestionType:'建议类型',priority_level:'优先级',priorityLevel:'优先级',
  source_sales_order_no:'来源销售订单',sourceSalesOrderNo:'来源销售订单',converted_order_no:'转出单号',convertedOrderNo:'转出单号',
  invoice_received_date:'收票日期',invoiceReceivedDate:'收票日期',supplier_invoice_no:'供应商发票号',supplierInvoiceNo:'供应商发票号',
  payment_method:'付款方式',paymentMethod:'付款方式',deposit_account:'入账账户',depositAccount:'入账账户',remittance_reference:'汇款参考',remittanceReference:'汇款参考',
  voucher_status:'凭证状态',voucherStatus:'凭证状态',posted_at:'过账时间',postedAt:'过账时间',posted_by:'过账人',postedBy:'过账人',
  attachment_url:'附件链接',attachmentUrl:'附件链接',external_reference:'外部参考号',externalReference:'外部参考号'
}
const qmsValueLabels:Record<string,string>={
  IQC:'来料检验',IPQC:'过程检验',FQC:'完工检验',OQC:'出货检验',
  PENDING:'待判定',OPEN:'待处理',REVIEWING:'评审中',IMPLEMENTING:'整改中',VERIFYING:'验证中',
  PASSED:'合格',FAILED:'不合格',REWORK:'返工',DOING:'返工中',DONE:'已完成',SCRAP:'报废',SCRAPPED:'已报废',
  CONCESSION:'让步接收',RETURN:'退货',RETURNED:'已退供',PARTIAL:'部分通过',MINOR:'轻微',MAJOR:'一般',CRITICAL:'严重',
  AQL:'AQL抽样',FIXED_COUNT:'固定数量抽样',PERCENTAGE:'百分比抽样',GENERAL_I:'一般检验I级',GENERAL_II:'一般检验II级',
  GENERAL_III:'一般检验III级',STRICT:'加严检验',REDUCED:'放宽检验',
}
export const zh=(value:unknown)=>{
  if(value==null||value==='')return '—'
  if(typeof value==='boolean')return value?'是':'否'
  const raw=String(value)
  const date=raw.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/)
  if(date)return date[1]+'年'+Number(date[2])+'月'+Number(date[3])+'日'+(date[4]?' '+date[4]+':'+date[5]:'')
  const code=raw.toUpperCase()
  const mesValues:Record<string,string>={
    CREATED:'待排产',RELEASED:'已下达',STARTED:'生产中',PAUSED:'已暂停',COMPLETED:'已完工',CANCELED:'已取消',
    DAY:'白班',NIGHT:'夜班',SHIFT_A:'甲班',SHIFT_B:'乙班',SHIFT_C:'丙班',
    RESPONDED:'已响应',RESOLVED:'已处理',OPEN:'待响应',CLOSED:'已关闭',
    MATERIAL:'缺料',EQUIPMENT:'设备异常',QUALITY:'质量异常',PROCESS:'工艺异常',SAFETY:'安全异常',OTHER:'其他异常',
    ROUTINE:'一般',URGENT:'紧急',EMERGENCY:'特急',NORMAL:'普通',HIGH:'高',MEDIUM:'中',LOW:'低'
  }
  return mesValues[code]||labels[code]||erpValueLabels[code]||crmValueLabels[code]||raw
}
export const zhQms=(value:unknown)=>{
  if(value==null||value==='')return '—'
  if(typeof value==='boolean')return value?'是':'否'
  const raw=String(value),date=raw.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/)
  if(date)return date[1]+'年'+Number(date[2])+'月'+Number(date[3])+'日'+(date[4]?' '+date[4]+':'+date[5]:'')
  return qmsValueLabels[raw.toUpperCase()]||zh(value)
}
const keyLabels:Record<string,string>={
  id:'编号',short_name:'客户简称',shortName:'客户简称',unified_social_code:'统一社会信用代码',unifiedSocialCode:'统一社会信用代码',customer_level:'客户等级',customerLevel:'客户等级',customer_type:'客户类型',customerType:'客户类型',region:'所属地区',credit_limit:'信用额度',creditLimit:'信用额度',credit_used:'已用额度',creditUsed:'已用额度',available_credit:'可用额度',availableCredit:'可用额度',payment_terms:'付款条件',paymentTerms:'付款条件',tax_no:'纳税识别号',taxNo:'纳税识别号',contact_person:'联系人',contactPerson:'联系人',contact_phone:'联系电话',contactPhone:'联系电话',contact_email:'联系邮箱',contactEmail:'联系邮箱',address:'客户地址',version_no:'版本',versionNo:'版本',change_reason:'变更原因',changeReason:'变更原因',updated_by:'更新人',updatedBy:'更新人',position_name:'职务',mobile:'手机号码',email:'电子邮箱',is_primary:'是否主要联系人',partner_type:'合作伙伴类型',partner_id:'合作伙伴编号',invoice_date:'开票日期',due_date:'到期日',invoice_amount:'应收金额',received_amount:'已回款金额',settled_amount:'已核销金额',settle_status:'核销状态',outstanding_amount:'未回款金额',overdue_days:'逾期天数',invoice_no:'发票号',cc_code:'成本中心',converted_order_no:'关联订单号',converted_at:'转化时间',
  lead_no:'线索编号',leadNo:'线索编号',customer_name:'客户名称',customerName:'客户名称',customer_code:'客户编码',customerCode:'客户编码',contact_name:'联系人',contactName:'联系人',contact_title:'联系人职务',contactTitle:'联系人职务',industry:'所属行业',source_channel:'来源渠道',sourceChannel:'来源渠道',intent_level:'意向程度',intentLevel:'意向程度',score:'线索评分',expected_budget:'预估预算',expectedBudget:'预估预算',preferred_contact_method:'偏好联系渠道',preferredContactMethod:'偏好联系渠道',expected_close_date:'预计成交日',expectedCloseDate:'预计成交日',
  opportunity_no:'商机编号',opportunityNo:'商机编号',opportunity_name:'商机名称',opportunityName:'商机名称',expect_amount:'预计金额',expectAmount:'预计金额',probability:'赢单概率',stage_code:'销售阶段',stageCode:'销售阶段',lead_source:'商机来源',leadSource:'商机来源',next_step:'下一步计划',nextStep:'下一步计划',forecast_category:'预测分类',forecastCategory:'预测分类',priority:'优先级',owner_user:'负责人账号',ownerUser:'负责人账号',owner_name:'负责人',ownerName:'负责人',dept_code:'部门编码',deptCode:'部门编码',expect_sign_date:'预计签约日',expectSignDate:'预计签约日',stage_updated_at:'阶段更新时间',
  quotation_no:'报价编号',quotationNo:'报价编号',total_amount:'报价总额',totalAmount:'报价总额',discount_rate:'折扣率',discountRate:'折扣率',gross_margin_rate:'毛利率',grossMarginRate:'毛利率',valid_until:'有效期至',validUntil:'有效期至',delivery_terms:'交付条件',deliveryTerms:'交付条件',ship_to_address:'收货地址',shipToAddress:'收货地址',currency:'币种',tax_rate:'税率',taxRate:'税率',notes:'报价说明',
  contract_no:'合同编号',contractNo:'合同编号',contract_name:'合同名称',contractName:'合同名称',quotation_id:'报价记录编号',quotationId:'报价记录编号',effective_date:'生效日期',effectiveDate:'生效日期',expire_date:'到期日期',expireDate:'到期日期',signing_date:'签署日期',signingDate:'签署日期',auto_renew:'自动续约',autoRenew:'自动续约',renewal_notice_days:'续约提前通知天数',renewalNoticeDays:'续约提前通知天数',special_terms:'特殊条款',specialTerms:'特殊条款',attachment_url:'合同附件链接',attachmentUrl:'合同附件链接',erp_sales_order_no:'ERP订单号',
  complaint_no:'客诉编号',complaintNo:'客诉编号',sales_order_no:'销售订单号',salesOrderNo:'销售订单号',delivery_no:'交付单号',deliveryNo:'交付单号',product_code:'产品编码',productCode:'产品编码',batch_no:'批次号',batchNo:'批次号',severity:'严重程度',problem_desc:'问题描述',problemDesc:'问题描述',responsible_dept:'责任部门',responsibleDept:'责任部门',response_due_at:'响应期限',responseDueAt:'响应期限',desired_resolution:'客户期望方案',desiredResolution:'客户期望方案',
  forecast_month:'预测月份',forecast_amount:'预测金额',weighted_amount:'加权预测金额',created_by:'创建人',createdBy:'创建人',created_at:'创建时间',createdAt:'创建时间',updated_at:'更新时间',updatedAt:'更新时间',status:'状态',lines:'报价明细',payment_plans:'回款计划',customer:'客户档案',contacts:'联系人',opportunities:'商机',quotations:'报价',contracts:'合同',receivables:'应收账款',complaints:'客诉',
  event_id:'事件编号',event_type:'事件类型',entity_type:'数据类型',entity_code:'数据编码',aggregate_type:'数据类型',aggregate_code:'数据编码',target_system:'目标系统',source_system:'来源系统',retry_count:'重试次数',error_message:'错误信息',occurred_at:'发生时间',processed_at:'处理时间',operator:'操作人',action:'操作',reason:'原因',old_code:'旧编码',current_code:'当前编码',survivor_code:'保留编码',merged_code:'合并编码',before_json:'变更前',after_json:'变更后',changed_at:'变更时间',ecr_no:'变更申请编号',ecr_title:'变更申请标题',eco_no:'变更命令编号',eco_title:'变更命令标题',ecn_no:'变更通知编号',ecn_title:'变更通知标题',run_no:'计划运行编号',suggestion_no:'建议编号',suggestion_type:'建议类型',material_code:'物料编码',quantity:'数量',receipt_no:'回款单号',payable_no:'应付单号',voucher_no:'凭证号',amount:'金额',unapplied_amount:'未核销金额'
}
Object.assign(keyLabels, {
  receipt_no:'收货单号', asn_no:'发货通知单号', batch_no:'批次号', supplier_lot_no:'供应商批次号', received_qty:'收货数量',
  inspection_no:'检验单号', warehouse_code:'仓库编码', warehouse_name:'仓库名称', location_code:'库位编码', location_name:'库位名称',
  location_type:'库位类型', zone_code:'库区编码', aisle_code:'巷道编码', rack_code:'货架编码', bin_code:'货格编码', max_weight_kg:'额定承重（千克）',
  on_hand_qty:'现有库存', allocated_qty:'已分配数量', available_qty:'可用数量', frozen_qty:'冻结数量', in_transit_qty:'在途数量',
  quality_status:'库存质量状态', quality_status_before:'变更前质量状态', quality_status_after:'变更后质量状态', received_date:'收货日期',
  last_in_date:'最近入库日期', last_out_date:'最近出库日期', unit_cost:'单位成本', total_cost:'库存成本', action_no:'操作编号',
  idempotency_key:'业务幂等号', action_type:'库存动作', source_system:'来源系统', source_no:'来源单号', operator_code:'操作人账号',
  on_hand_delta:'现有库存变动', available_delta:'可用库存变动', frozen_delta:'冻结库存变动', putaway_no:'上架单号',
  suggested_location:'推荐库位', actual_location:'实际库位', pallet_sscc:'托盘SSCC编码', expiry_date:'有效期至', handling_note:'装卸与上架说明',
  transfer_no:'调拨单号', from_warehouse:'调出仓库', from_location:'调出库位', to_warehouse:'调入仓库', to_location:'调入库位',
  source_reference:'外部参考单号', requested_by:'申请人', count_no:'盘点单号', scope_type:'盘点范围', planned_date:'计划日期',
  count_reason:'盘点原因', owner_user:'负责人账号', book_qty:'账面数量', actual_qty:'实盘数量', difference_qty:'盘点差异', review_status:'复核状态',
  locationCode:'库位编码', locationName:'库位名称', warehouseCode:'仓库编码', warehouseName:'仓库名称', locationType:'库位类型',
  zoneCode:'库区编码', aisleCode:'巷道编码', rackCode:'货架编码', binCode:'货格编码', maxWeightKg:'额定承重（千克）',
  palletSscc:'托盘SSCC编码', expiryDate:'有效期至', handlingNote:'装卸与上架说明', sourceReference:'外部参考单号',
  requestedBy:'申请人', countReason:'盘点原因', ownerUser:'负责人账号', scopeType:'盘点范围', actualQty:'实盘数量',
  bookQty:'账面数量', differenceQty:'盘点差异', reviewStatus:'复核状态', idempotencyKey:'业务幂等号', actionType:'库存动作',
  sourceSystem:'来源系统', sourceNo:'来源单号', operatorCode:'操作人账号', qualityStatus:'库存质量状态', createdBy:'创建人'
})
Object.assign(keyLabels, {
  application_no:'准入申请编号',supplier_name:'供应商名称',category_code:'供应品类',contact_name:'联系人',contact_phone:'联系电话',risk_level:'风险等级',
  contact_email:'联系邮箱',credit_code:'统一社会信用代码',registered_address:'注册地址',supplier_type:'供应商类型',payment_terms:'付款条件',
  currency_code:'币种',qualification_note:'准入补充说明',onboarding_id:'准入申请记录号',qualification_type:'资质类别',certificate_no:'证书编号',
  issue_date:'发证日期',expire_date:'到期日期',audit_result:'审核结果',issued_by:'发证机构',file_name:'证书文件名称',
  rfq_no:'询价单号',title:'询价主题',material_code:'物料编码',quantity:'需求数量',required_date:'需求日期',winner_supplier_code:'中标供应商编码',
  source_requisition_no:'来源采购申请单号',delivery_terms:'交付条款',ship_to_address:'收货地址',invited_supplier_count:'邀请供应商数',
  purchasing_group:'采购组织或采购组',technical_requirement:'技术与质量要求',rfq_id:'询价记录号',unit_price:'未税单价',
  delivery_date:'承诺交货日期',tax_rate:'税率（%）',minimum_order_qty:'最小起订量',payment_term_days:'付款账期（天）',
  freight_amount:'运费金额（元）',valid_until:'报价有效期至',warranty_months:'质保期（月）',attachment_name:'报价文件名称',
  purchase_order_no:'采购订单号',supplier_code:'供应商编码',material_name:'物料名称',
  order_qty:'订购数量',received_qty:'已收数量',unit_code:'计量单位',total_amount:'订单总额',expected_date:'要求交货日期',
  promised_date:'供应商承诺日期',prod_order_no:'关联生产订单',shipping_terms:'运输条款',buyer_name:'采购员',
  supplier_reference_no:'供应商参考号',supplier_note:'订单备注',order_status:'订单状态',
  asn_no:'发货通知单号',batch_no:'供应商批次',ship_qty:'发运数量',ship_date:'发运日期',
  expected_arrival_date:'预计到货日期',carrier_name:'承运单位',tracking_no:'物流单号',packing_slip_no:'送货单号',
  issue_no:'问题编号',source_no:'来源单号',defect_qty:'不合格数量',ppm:'百万分率',eight_d_status:'8D整改状态',
  root_cause:'根本原因',containment_action:'临时遏制措施',corrective_action:'纠正措施',preventive_action:'预防措施',
  owner_user:'整改负责人',close_note:'验证关闭说明',period_code:'统计期间',delivery_rate:'准时交付率',
  qualified_rate:'来料合格率',score:'综合得分',rating:'供应商等级',website:'官方网站',note:'备注',
  updated_at:'更新时间',created_by:'创建人',closed_at:'关闭时间',created_at:'创建时间'
})
const crmKeyLabels:Record<string,string>={
  lead_source:'线索来源',leadSource:'线索来源',lead_id:'线索记录编号',follow_type:'跟进方式',followType:'跟进方式',
  follow_content:'跟进内容',content:'跟进内容',next_follow_at:'下次跟进时间',nextFollowAt:'下次跟进时间',
  assignment_id:'分配记录编号',from_user:'原负责人',to_user:'新负责人',assigned_by:'分配人',reason:'原因',
  standard_price:'标准单价',standardPrice:'标准单价',sales_price:'销售单价',salesPrice:'销售单价',
  standard_cost:'标准成本',standardCost:'标准成本',line_amount:'明细金额',lineAmount:'明细金额',
  line_cost:'明细成本',lineCost:'明细成本',line_no:'明细序号',lineNo:'明细序号',qty:'数量',unit_code:'计量单位',
  tax_amount:'税额',total_cost:'总成本',plan_ratio:'计划比例',plan_amount:'计划金额',received_amount:'已回款金额',
  period_no:'期数',payment_terms:'付款条件',penalty_terms:'违约条款',delivery_terms:'交付条件',
  source_lead_no:'来源线索编号',sourceLeadNo:'来源线索编号',converted_opportunity_no:'转入商机编号',
  win_rate:'赢单概率',stage_updated_at:'阶段更新时间',expected_close_date:'预计成交日期',expect_sign_date:'预计签约日期',
  workflow_instance_id:'审批流程编号',closed_at:'关闭时间',closed_by:'关闭人',solution:'处理方案',
  customer_feedback:'客户反馈',response_due_at:'响应期限',desired_resolution:'客户期望方案',
  created_at:'创建时间',createdAt:'创建时间',updated_at:'更新时间',updatedAt:'更新时间',created_by:'创建人',createdBy:'创建人',
  updated_by:'更新人',owner_user:'负责人账号',owner_name:'负责人',dept_code:'部门编码',
  lost_reason:'丢单原因',remark:'备注',description:'业务描述',notes:'说明',attachment_url:'附件链接',
  auto_renew:'自动续约',autoRenew:'自动续约',renewal_notice_days:'续约提前通知天数',
  invoice_no:'发票号',invoice_date:'开票日期',settle_status:'核销状态',is_primary:'是否主要联系人',
  phone:'联系电话',telephone:'联系电话',website:'官方网站',industry:'所属行业',annual_revenue:'年营业额',
  employee_count:'员工人数',customer_source:'客户来源',customer_status:'客户状态',credit_status:'信用状态',
  last_contacted:'最近联系时间',last_activity_date:'最近活动时间',external_id:'外部系统编号',
  warehouse_code:'仓库编码',warehouse_name:'仓库名称',warehouse_type:'仓库类型',factory_code:'工厂编码',work_center_code:'工作中心编码',work_center_name:'工作中心名称',workshop_code:'车间编码',capacity_per_day:'日产能',production_version_code:'生产版本编码',bom_code:'BOM编码',bom_name:'BOM名称',bom_type:'BOM类型',bom_version:'BOM版本',routing_code:'工艺路线编码',routing_name:'工艺路线名称',routing_version:'工艺路线版本',effective_date:'生效日期',expire_date:'失效日期',category_code:'分类编码',category_name:'分类名称',parent_id:'上级记录',category_level:'分类层级',is_leaf:'是否末级分类',category_path:'分类路径',unit_name:'单位名称',unit_type:'单位类型',base_unit_code:'基本单位',convert_rate:'换算率',org_code:'组织编码',org_name:'组织名称',org_type:'组织类型',org_level:'组织层级',manager_user_id:'负责人编号',manager_emp_id:'负责人员工编号',cc_code:'成本中心编码',cc_name:'成本中心名称',cc_type:'成本中心类型',org_id:'所属组织编号',subject_code:'科目编码',subject_name:'科目名称',subject_type:'科目类型',subject_level:'科目层级',current:'是否当前版本',op_seq:'工序顺序',operation_code:'工序编码',operation_name:'工序名称',work_center:'工作中心',setup_time_min:'准备时间（分钟）',run_time_min:'单件工时（分钟）',wait_time_min:'等待时间（分钟）',key_operation:'是否关键工序',inspection_op:'是否检验工序',inspection_required:'是否要求检验',default_equipment_code:'默认设备编码',required_skill:'所需技能',qty_per:'单位用量',priority_no:'优先级',conversion_rate:'换算率',substitute_material_code:'替代物料编码',supplier_level:'供应商等级',supplier_type:'供应商类型',qual_status:'资质状态',qual_expire_date:'资质到期日',lead_time_days:'交货提前期（天）',supply_category:'供应类别',product_model:'产品型号',lifecycle_status:'生命周期',weight_kg:'单重（千克）',launch_date:'上市日期',eol_date:'停产日期',
  quotation_id:'报价记录编号',contract_id:'合同记录编号',complaint_id:'客诉记录编号',opportunity_id:'商机记录编号',
  warehouseCode:'仓库编码',warehouseName:'仓库名称',warehouseType:'仓库类型',factoryCode:'工厂编码',workCenterCode:'工作中心编码',workCenterName:'工作中心名称',workshopCode:'车间编码',capacityPerDay:'日产能',productionVersionCode:'生产版本编码',bomCode:'BOM编码',bomName:'BOM名称',bomType:'BOM类型',bomVersion:'BOM版本',routingCode:'工艺路线编码',routingName:'工艺路线名称',routingVersion:'工艺路线版本',effectiveDate:'生效日期',expireDate:'失效日期',categoryCode:'分类编码',categoryName:'分类名称',parentId:'上级记录',categoryLevel:'分类层级',leaf:'是否末级分类',categoryPath:'分类路径',unitCode:'单位编码',unitName:'单位名称',unitType:'单位类型',baseUnitCode:'基本单位',convertRate:'换算率',orgCode:'组织编码',orgName:'组织名称',orgType:'组织类型',orgLevel:'组织层级',managerUserId:'负责人编号',managerEmpId:'负责人员工编号',ccCode:'成本中心编码',ccName:'成本中心名称',ccType:'成本中心类型',orgId:'所属组织编号',subjectCode:'科目编码',subjectName:'科目名称',subjectType:'科目类型',subjectLevel:'科目层级',isLeaf:'是否明细科目',opSeq:'工序顺序',operationCode:'工序编码',operationName:'工序名称',workCenter:'工作中心',setupTimeMin:'准备时间（分钟）',runTimeMin:'单件工时（分钟）',waitTimeMin:'等待时间（分钟）',keyOperation:'是否关键工序',inspectionOp:'是否检验工序',inspectionRequired:'是否要求检验',defaultEquipmentCode:'默认设备编码',requiredSkill:'所需技能',qtyPer:'单位用量',priorityNo:'优先级',conversionRate:'换算率',substituteMaterialCode:'替代物料编码',supplierLevel:'供应商等级',supplierType:'供应商类型',qualStatus:'资质状态',qualExpireDate:'资质到期日',leadTimeDays:'交货提前期（天）',supplyCategory:'供应类别',paymentTerms:'付款条件',productModel:'产品型号',lifecycleStatus:'生命周期',weightKg:'单重（千克）',launchDate:'上市日期',eolDate:'停产日期',
  product_name:'产品名称',material_name:'物料名称',customer_code:'客户编码',supplier_code:'供应商编码',
  quote_to_name:'报价对象',ship_to_address:'收货地址',valid_until:'有效期至',expected_budget:'预估预算',
  preferred_contact_method:'偏好联系渠道',response_time:'响应时间',resolution_time:'解决时间'
}
const mesKeyLabels:Record<string,string>={
  workOrderNo:'工单号',prodOrderNo:'生产订单号',productCode:'产品编码',opSeq:'工序序号',operationCode:'工序编码',operationName:'工序名称',
  equipmentCode:'设备编码',planQty:'计划数量',completedQty:'已报工数量',qualifiedQty:'合格数量',scrapQty:'报废数量',status:'单据状态',
  routingCode:'工艺路线编码',routingVersion:'工艺路线版本',workCenter:'工作中心',workshopCode:'车间编码',planStartTime:'计划开工时间',
  planEndTime:'计划完工时间',planHours:'计划工时',actualHours:'实际工时',workshopUser:'派工负责人',priorityLevel:'派工优先级',
  batchNo:'生产批次',shiftCode:'班次',remark:'补充说明',actualStartTime:'实际开工时间',actualEndTime:'实际完工时间',createdAt:'创建时间',updatedAt:'更新时间',
  reportNo:'报工单号',reportQty:'报工总数',reportDate:'报工日期',startTime:'开始时间',endTime:'结束时间',workHours:'作业工时',
  operatorCode:'操作人员',paused:'是否暂停',pauseReason:'停机原因',pauseMinutes:'停机分钟数',createdBy:'创建人',updatedBy:'修改人',
  eventNo:'异常单号',eventType:'异常类型',severity:'异常等级',description:'异常描述',affectedQty:'影响数量',downtimeMinutes:'停线分钟数',
  responseDueAt:'期望响应时间',reportedBy:'提报人',reportedAt:'提报时间',responseBy:'响应人',responseAt:'响应时间',responseNote:'响应说明',
  resolution:'处理方案与结果',resolvedBy:'处理人',resolvedAt:'处理时间',eventStatus:'异常状态',id:'记录编号'
}
const qmsKeyLabels:Record<string,string>={
  standard_code:'标准编码',standard_name:'标准名称',inspection_type:'检验类型',material_code:'物料编码',version_no:'版本号',aql_level:'AQL等级',
  effective_date:'生效日期',owner_user:'负责人账号',reference_doc:'引用文件',remark:'备注',sampling_method:'抽样方式',inspection_level:'检验水平',
  sample_unit:'样本单位',lot_min:'最小批量',lot_max:'最大批量',lot_size_min:'最小批量',lot_size_max:'最大批量',sample_size:'样本数量',accept_qty:'接收数',reject_qty:'拒收数',
  inspection_no:'检验单号',source_type:'来源类型',source_no:'来源单号',material_name:'物料名称',batch_no:'批次',supplier_code:'供应商编码',
  inspected_qty:'送检数量',sample_qty:'抽样数量',qualified_qty:'合格数量',defect_qty:'不合格数量',inspect_result:'检验结论',result:'检验结论',inspect_date:'检验日期',inspector_code:'检验人',
  prod_order_no:'生产订单号',work_order_no:'生产工单号',operation_code:'工序编码',delivery_no:'送货单号',workshop_code:'车间编码',
  inspection_basis:'检验依据',inspection_method:'检验方法',equipment_code:'检验设备编号',environment_temp:'环境温度（℃）',environment_humidity:'环境湿度（%）',
  defect_no:'不合格品单号',defect_type:'缺陷类型',defect_desc:'缺陷描述',defect_level:'严重程度',disposition:'处置方式',disposition_qty:'处置数量',
  disposition_at:'处置时间',disposition_by:'处置人',responsible_dept:'责任部门',root_cause:'根本原因',containment_action:'临时遏制措施',location_desc:'发生位置',
  ncr_no:'NCR编号',severity:'严重程度',due_date:'完成期限',status:'单据状态',capa_no:'CAPA编号',corrective_action:'纠正措施',preventive_action:'预防措施',
  effectiveness_criteria:'有效性判据',effectiveness_result:'有效性结论',effectiveness_notes:'验证说明',verified_by:'验证人',verified_at:'验证时间',closed_at:'关闭时间',closed_by:'关闭人',
  rework_no:'返工单号',rework_qty:'返工数量',rework_hours:'返工工时',delay_days:'延期天数',rework_status:'返工状态',start_time:'开工时间',end_time:'完工时间',
  rework_method:'返工方法',verification_result:'复验结论',scrap_qty:'报废数量',rework_remark:'返工备注',eight_d_no:'8D编号',team_members:'整改小组成员',problem_desc:'问题描述',
  created_at:'创建时间',updated_at:'更新时间',created_by:'创建人',source_requisition_no:'来源申请单号'
}
export const zhKey=(value:string)=>{
  const normalized=value.replace(/[A-Z]/g,letter=>`_${letter.toLowerCase()}`)
  return mesKeyLabels[value]||qmsKeyLabels[value]||qmsKeyLabels[normalized]||keyLabels[value]||erpKeyLabels[value]||crmKeyLabels[value]||value.replace(/([a-z0-9])([A-Z])/g,'$1 $2').replaceAll('_',' ')
}
