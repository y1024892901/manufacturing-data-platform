const labels: Record<string,string> = {
  DRAFT:'草稿', PENDING:'待处理', PROCESSING:'处理中', APPROVING:'审批中', APPROVED:'已同意',
  PUBLISHED:'已发布', RELEASED:'已发布', REJECTED:'已驳回', FAILED:'失败', SUCCESS:'成功',
  RETRYING:'重试中', DEAD:'人工处理', COMPLETED:'已完成', RUNNING:'进行中', CANCELLED:'已撤回',
  ACTIVE:'启用', ENABLED:'启用', DISABLED:'停用', INACTIVE:'停用', LOCKED:'已锁定', CHANGING:'变更中',
  DESIGN:'设计阶段', TRIAL:'试制阶段', MASS:'量产准备', PROD:'量产阶段', EOL:'停止生产', NORMAL:'普通',
  HIGH:'高', MEDIUM:'中', LOW:'低', URGENT:'紧急', PUBLIC:'公开', INTERNAL:'内部', CONFIDENTIAL:'机密',
  DRAWING:'图纸', PROCESS_CARD:'工艺卡', WORK_INSTRUCTION:'作业指导书', SPECIFICATION:'技术规范',
  CUSTOMER:'客户', SUPPLIER:'供应商', MATERIAL:'物料', PRODUCT:'产品', BOM:'物料清单', ROUTING:'工艺路线',
  CREATE:'新增', UPDATE:'修改', DELETE:'删除', APPROVE:'同意', REJECT:'驳回', SUBMIT:'提交',
  TRANSFER:'转交', ADD_SIGN:'加签', COPY:'抄送', CANCEL:'撤回', COMPLETE:'完成', SYSTEM:'系统', EFFECTIVE:'已生效', EXPIRED:'已失效', WON:'赢单', LOST:'丢单', QUALIFY:'需求确认', PROPOSAL:'方案报价', NEGOTIATE:'商务谈判', PASSED:'已通过', EXCEPTION:'例外', UNCHECKED:'未检查', COMMITTED:'已承诺', PARTIAL:'部分满足', SETTLED:'已核销', OPEN:'待处理', ISSUED:'已开票', UNAPPLIED:'未核销', APPLIED:'已核销', PROPOSED:'建议中', CONFIRMED:'已确认'
}
export const zh = (value: unknown) => value == null || value === '' ? '—' : (labels[String(value).toUpperCase()] || String(value))
export const zhKey = (value:string) => ({id:'编号',event_id:'事件编号',event_type:'事件类型',entity_type:'数据类型',entity_code:'数据编码',aggregate_type:'数据类型',aggregate_code:'数据编码',target_system:'目标系统',source_system:'来源系统',status:'状态',version:'版本',retry_count:'重试次数',error_message:'错误信息',occurred_at:'发生时间',processed_at:'处理时间',created_at:'创建时间',updated_at:'更新时间',operator:'操作人',action:'操作',reason:'原因',old_code:'旧编码',current_code:'当前编码',survivor_code:'保留编码',merged_code:'合并编码',before_json:'变更前',after_json:'变更后',changed_at:'变更时间',ecr_no:'变更申请编号',ecr_title:'变更申请标题',eco_no:'变更命令编号',eco_title:'变更命令标题',ecn_no:'变更通知编号',ecn_title:'变更通知标题',product_code:'产品编码',urgency:'紧急程度',planned_effective_date:'计划生效日',lead_no:'线索编号',customer_name:'客户名称',opportunity_no:'商机编号',opportunity_name:'商机名称',quotation_no:'报价编号',contract_no:'合同编号',sales_order_no:'销售订单号',total_amount:'总金额',discount_rate:'折扣率',gross_margin_rate:'毛利率',credit_status:'信用状态',atp_status:'交期承诺状态',run_no:'计划运行编号',suggestion_no:'建议编号',suggestion_type:'建议类型',material_code:'物料编码',quantity:'数量',invoice_no:'发票号',receipt_no:'回款单号',payable_no:'应付单号',voucher_no:'凭证号',amount:'金额',unapplied_amount:'未核销金额',outstanding_amount:'未收金额',forecast_month:'预测月份',forecast_amount:'预测金额',weighted_amount:'加权预测金额'} as Record<string,string>)[value]||value
