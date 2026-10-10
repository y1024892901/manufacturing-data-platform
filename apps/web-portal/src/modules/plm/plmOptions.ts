import http from '../../api/http'
import { zh } from '../../shared/display'

export type PlmProductOption = { value: string; label: string }

const labels: Record<string, string> = {
  DRAFT: '草稿', ANALYZED: '已完成影响分析', ECO_CREATED: '已生成变更命令',
  REVIEWING: '审批中', REJECTED: '已驳回', APPROVED: '已批准', IMPLEMENTED: '已实施',
  RELEASED: '已发布', ACTIVE: '启用', DISABLED: '停用',
  LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急',
  BOM: '物料清单', ROUTING: '工艺路线', DESIGN: '设计变更', MATERIAL: '物料变更', PROCESS: '工艺变更',
  MEDIUM: '中', TRIAL: '试制', MASS: '量产', PROD: '生产', EOL: '停产',
  'zh-CN': '中文', 'EN-US': '英文', PUBLIC: '公开', INTERNAL: '内部', CONFIDENTIAL: '机密',
  DRAWING: '图纸', PROCESS_CARD: '工艺卡', WORK_INSTRUCTION: '作业指导书', SPECIFICATION: '技术规范',
}

export function plmLabel(value: unknown): string {
  const key = String(value ?? '')
  return labels[key] || zh(value)
}

export async function loadPlmProducts(): Promise<PlmProductOption[]> {
  const { data } = await http.get('/plm/options/products')
  const rows = data.data
  if (!Array.isArray(rows)) return []
  return rows.map((row: Record<string, unknown>) => {
    const code = String(row.product_code ?? row.productCode ?? '')
    const name = String(row.product_name ?? row.productName ?? '')
    const model = String(row.product_model ?? row.productModel ?? '')
    const lifecycle = String(row.lifecycle_status ?? row.lifecycleStatus ?? '')
    return {
      value: code,
      label: [name ? `${name}（${code}）` : code, model, lifecycle ? plmLabel(lifecycle) : ''].filter(Boolean).join(' · '),
    }
  }).filter((option: PlmProductOption) => option.value)
}
