package com.mfg.security.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.principal.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NavigationController {
    private static final Map<String, List<MenuItem>> MENUS = Map.ofEntries(
            Map.entry("mdm", List.of(new MenuItem("系统首页", "/mdm/dashboard"), new MenuItem("物料主数据", "/mdm/materials"), new MenuItem("产品主数据", "/mdm/products"), new MenuItem("BOM与替代料", "/mdm/boms"), new MenuItem("工艺路线", "/mdm/routings"), new MenuItem("客户与联系人", "/mdm/customers"), new MenuItem("供应商与联系人", "/mdm/suppliers"), new MenuItem("仓库", "/mdm/warehouses"), new MenuItem("工作中心", "/mdm/work-centers"), new MenuItem("生产版本", "/mdm/production-versions"), new MenuItem("主数据治理", "/mdm/governance"), new MenuItem("物料分类", "/mdm/categories"), new MenuItem("单位换算", "/mdm/units"), new MenuItem("组织主数据", "/mdm/organizations"), new MenuItem("成本中心", "/mdm/cost-centers"), new MenuItem("会计科目", "/mdm/subjects"))),
            Map.entry("crm", List.of(new MenuItem("系统首页", "/crm/dashboard"), new MenuItem("客户360", "/crm/customers"), new MenuItem("销售线索", "/crm/leads"), new MenuItem("商机管理", "/crm/opportunities"), new MenuItem("报价管理", "/crm/quotations"), new MenuItem("合同管理", "/crm/contracts"), new MenuItem("销售预测", "/crm/forecast"), new MenuItem("客诉协同", "/crm/complaints"))),
            Map.entry("erp", List.of(new MenuItem("系统首页", "/erp/dashboard"), new MenuItem("销售订单", "/erp/sales-orders"), new MenuItem("信用管理", "/erp/credits"), new MenuItem("交期承诺", "/erp/atp"), new MenuItem("物料需求计划", "/erp/mrp"), new MenuItem("计划建议", "/erp/suggestions"), new MenuItem("生产订单", "/erp/production-orders"), new MenuItem("应收账款", "/erp/receivables"), new MenuItem("销售发票", "/erp/invoices"), new MenuItem("回款核销", "/erp/receipts"), new MenuItem("应付账款", "/erp/payables"), new MenuItem("财务凭证", "/erp/vouchers"), new MenuItem("成本毛利", "/erp/margins"))),
            Map.entry("plm", List.of(new MenuItem("系统首页", "/plm/dashboard"), new MenuItem("产品族", "/plm/families"), new MenuItem("产品版本", "/plm/products"), new MenuItem("EBOM/MBOM", "/plm/boms"), new MenuItem("ECR/ECO/ECN", "/plm/ecns"), new MenuItem("工程基线", "/plm/baselines"), new MenuItem("受控文档", "/plm/documents"))),
            Map.entry("srm", List.of(new MenuItem("系统首页", "/srm/dashboard"), new MenuItem("供应商准入", "/srm/onboarding"), new MenuItem("询报价与定标", "/srm/rfq"), new MenuItem("采购订单", "/srm/purchase-orders"), new MenuItem("ASN到货协同", "/srm/deliveries"), new MenuItem("供应商质量", "/srm/quality"), new MenuItem("供应商绩效", "/srm/performance"))),
            Map.entry("wms", List.of(new MenuItem("系统首页", "/wms/dashboard"), new MenuItem("收货待检", "/wms/receipts"), new MenuItem("推荐上架", "/wms/locations"), new MenuItem("库存余额", "/wms/inventory"), new MenuItem("库存状态作业", "/wms/transactions"), new MenuItem("库存调拨", "/wms/transfers"), new MenuItem("盘点作业", "/wms/counting"))),
            Map.entry("mes", List.of(new MenuItem("系统首页", "/mes/dashboard"), new MenuItem("生产工单", "/mes/work-orders"), new MenuItem("派工排产", "/mes/dispatch"), new MenuItem("报工明细", "/mes/reports"), new MenuItem("Andon异常", "/mes/andon"))),
            Map.entry("qms", List.of(new MenuItem("系统首页", "/qms/dashboard"), new MenuItem("检验标准", "/qms/standards"), new MenuItem("抽样方案", "/qms/sampling"), new MenuItem("质量检验", "/qms/inspections"), new MenuItem("NCR不合格评审", "/qms/defects"), new MenuItem("返工作业", "/qms/reworks"), new MenuItem("CAPA", "/qms/capas"), new MenuItem("供应商8D", "/qms/8d"))),
            Map.entry("eam", List.of(new MenuItem("系统首页", "/eam/dashboard"), new MenuItem("设备台账", "/eam/equipments"), new MenuItem("点检计划", "/eam/inspections"), new MenuItem("设备故障", "/eam/faults"), new MenuItem("维修执行", "/eam/repairs"))),
            Map.entry("energy", List.of(new MenuItem("系统首页", "/energy/dashboard"), new MenuItem("能源仪表", "/energy/meters"), new MenuItem("设备能耗", "/energy/equipment"), new MenuItem("车间能耗", "/energy/workshops"), new MenuItem("能耗告警", "/energy/alerts")))
    );

    @GetMapping("/navigation")
    @PreAuthorize("hasAuthority('SYS:NAV:VIEW')")
    public ApiResponse<List<MenuItem>> navigation(@RequestParam String system) {
        String code = system.toLowerCase(Locale.ROOT);
        LoginUser user = CurrentUser.get();
        boolean admin = user.getRoleCodes().contains("ADMIN");
        if (!admin && !user.getSystems().contains(code))
            throw BizException.of(ErrorCode.FORBIDDEN, "无权访问该业务系统");
        List<MenuItem> items = MENUS.get(code);
        if (items == null) throw BizException.of(ErrorCode.PARAM_INVALID, "未知系统编码");
        return ApiResponse.ok(items);
    }

    public record MenuItem(String label, String route) {}
}
