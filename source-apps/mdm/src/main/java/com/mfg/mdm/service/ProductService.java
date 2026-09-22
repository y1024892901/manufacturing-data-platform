package com.mfg.mdm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mdm.entity.Material;
import com.mfg.mdm.entity.Product;
import com.mfg.mdm.repo.MaterialRepository;
import com.mfg.mdm.repo.ProductRepository;
import com.mfg.security.config.CurrentUser;
import com.mfg.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ProductService {
    private final ProductRepository products;
    private final MaterialRepository materials;
    private final MasterDataService masterData;

    @Transactional
    public Product create(Product input) {
        if (products.existsByProductCode(input.getProductCode()))
            throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS, "产品编码已存在：" + input.getProductCode());
        validateMaterial(input.getMaterialId());
        input.setCreatedBy(CurrentUser.usernameOrSystem());
        return masterData.create(input, products);
    }

    @Transactional
    public Product update(Long id, Product input) {
        Product product = load(id);
        product.setProductName(input.getProductName()); product.setProductModel(input.getProductModel());
        product.setMaterialId(input.getMaterialId()); product.setCategoryId(input.getCategoryId());
        product.setUnitCode(input.getUnitCode()); product.setWeightKg(input.getWeightKg());
        product.setLifecycleStatus(input.getLifecycleStatus()); product.setLaunchDate(input.getLaunchDate());
        product.setEolDate(input.getEolDate());
        validateMaterial(product.getMaterialId());
        return masterData.update(product, products, input.getChangeReason());
    }

    @Transactional public WfInstance submit(Long id) { return masterData.submitForApproval(load(id), products); }
    private Product load(Long id) { return products.findById(id).orElseThrow(() -> BizException.notFound("产品", id)); }
    private void validateMaterial(Long id) {
        if (id == null) return;
        Material material = materials.findById(id).orElseThrow(() -> BizException.notFound("成品物料", id));
        masterData.assertConsumable(material, "产品主数据");
    }
}
