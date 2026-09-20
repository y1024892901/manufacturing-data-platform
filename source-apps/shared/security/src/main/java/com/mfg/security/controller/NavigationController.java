package com.mfg.security.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.principal.LoginUser;
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
            Map.entry("mdm", List.of(new MenuItem("系统首页", "/mdm/dashboard"), new MenuItem("物料主数据", "/mdm/materials"), new MenuItem("BOM版本", "/mdm/boms"), new MenuItem("客户主数据", "/mdm/customers"), new MenuItem("供应商主数据", "/mdm/suppliers"), new MenuItem("物料分类", "/mdm/categories"), new MenuItem("单位换算", "/mdm/units"), new MenuItem("组织主数据", "/mdm/organizations"), new MenuItem("成本中心", "/mdm/cost-centers"), new MenuItem("会计科目", "/mdm/subjects"))),
            Map.entry("crm", List.of(new MenuItem("系统首页", "/crm/dashboard"), new MenuItem("客户360", "/crm/customers"), new MenuItem("销售线索", "/crm/leads"), new MenuItem("商机管理", "/crm/opportunities"), new MenuItem("报价合同", "/crm/quotations"))),
            Map.entry("erp", List.of(new MenuItem("系统首页", "/erp/dashboard"), new MenuItem("销售订单", "/erp/sales-orders"), new MenuItem("生产订单", "/erp/production-orders"), new MenuItem("MRP计划", "/erp/mrp"), new MenuItem("财务凭证", "/erp/vouchers"))),
            Map.entry("plm", List.of(new MenuItem("系统首页", "/plm/dashboard"), new MenuItem("产品结构", "/plm/products"), new MenuItem("EBOM/MBOM", "/plm/boms"), new MenuItem("工程变更", "/plm/ecns"), new MenuItem("受控文档", "/plm/documents"))),
            Map.entry("srm", List.of(new MenuItem("系统首页", "/srm/dashboard"), new MenuItem("供应商准入", "/srm/onboarding"), new MenuItem("询报价", "/srm/rfq"), new MenuItem("采购订单", "/srm/purchase-orders"), new MenuItem("到货协同", "/srm/deliveries"))),
            Map.entry("wms", List.of(new MenuItem("系统首页", "/wms/dashboard"), new MenuItem("仓库库位", "/wms/locations"), new MenuItem("库存余额", "/wms/inventory"), new MenuItem("出入库流水", "/wms/transactions"), new MenuItem("盘点作业", "/wms/counting"))),
            Map.entry("mes", List.of(new MenuItem("系统首页", "/mes/dashboard"), new MenuItem("生产工单", "/mes/work-orders"), new MenuItem("派工排产", "/mes/dispatch"), new MenuItem("报工明细", "/mes/reports"), new MenuItem("Andon异常", "/mes/andon"))),
            Map.entry("qms", List.of(new MenuItem("系统首页", "/qms/dashboard"), new MenuItem("检验标准", "/qms/standards"), new MenuItem("质量检验", "/qms/inspections"), new MenuItem("不合格品", "/qms/defects"), new MenuItem("返工与CAPA", "/qms/reworks"))),
            Map.entry("eam", List.of(new MenuItem("系统首页", "/eam/dashboard"), new MenuItem("设备台账", "/eam/equipments"), new MenuItem("点检计划", "/eam/inspections"), new MenuItem("设备故障", "/eam/faults"), new MenuItem("维修执行", "/eam/repairs"))),
            Map.entry("energy", List.of(new MenuItem("系统首页", "/energy/dashboard"), new MenuItem("能源仪表", "/energy/meters"), new MenuItem("设备能耗", "/energy/equipment"), new MenuItem("车间能耗", "/energy/workshops"), new MenuItem("能耗告警", "/energy/alerts")))
    );

    @GetMapping("/navigation")
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
