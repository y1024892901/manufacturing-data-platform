import http from '../api/http'

export type MdmOption = { label: string; value: string | number }
export type MdmMaterialOption = {
  label: string
  value: string
  materialCode: string
  materialName: string
  materialSpec?: string | null
  baseUnitCode: string
  purchaseUnitCode?: string | null
  standardPrice?: number | null
}
export type MdmUnitOption = Omit<MdmOption, 'value'> & { value: string; unitName: string; unitType: string; baseUnitCode?: string | null; convertRate: number }

function responseRows(result: any): Record<string, any>[] {
  const data = result?.data?.data
  return Array.isArray(data) ? data : []
}

export async function loadMdmMaterials(): Promise<MdmMaterialOption[]> {
  const response = await http.get('/mdm/options/materials')
  return responseRows(response).map(row => {
    const code = String(row.material_code ?? row.materialCode ?? '')
    const name = String(row.material_name ?? row.materialName ?? '')
    const spec = row.material_spec ?? row.materialSpec ?? ''
    return {
      label: `${code} · ${name}${spec ? ` · ${spec}` : ''}`,
      value: code,
      materialCode: code,
      materialName: name,
      materialSpec: spec || null,
      baseUnitCode: String(row.base_unit_code ?? row.baseUnitCode ?? ''),
      purchaseUnitCode: row.purchase_unit_code ?? row.purchaseUnitCode ?? null,
      standardPrice: row.standard_price ?? row.standardPrice ?? null,
    }
  }).filter(option => option.value)
}

export async function loadMdmUnits(): Promise<MdmUnitOption[]> {
  const response = await http.get('/mdm/options/units')
  return responseRows(response).map(row => {
    const code = String(row.unit_code ?? row.unitCode ?? '')
    const name = String(row.unit_name ?? row.unitName ?? '')
    return {
      label: `${name || code}${name ? `（${code}）` : ''}`,
      value: code,
      unitName: name || code,
      unitType: String(row.unit_type ?? row.unitType ?? ''),
      baseUnitCode: row.base_unit_code ?? row.baseUnitCode ?? null,
      convertRate: Number(row.convert_rate ?? row.convertRate ?? 1),
    }
  }).filter(option => option.value)
}

export async function loadMdmCategories(): Promise<MdmOption[]> {
  const response = await http.get('/mdm/options/categories')
  return responseRows(response).map(row => {
    const code = String(row.category_code ?? row.categoryCode ?? '')
    const name = String(row.category_name ?? row.categoryName ?? '')
    const path = String(row.category_path ?? row.categoryPath ?? '')
    return { label: `${path || name || code}（${code}）`, value: Number(row.id) }
  }).filter(option => option.value)
}
