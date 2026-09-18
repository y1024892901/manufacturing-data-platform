package com.mfg.mdm.service;
import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mdm.dto.BomCommand;
import com.mfg.mdm.entity.*;
import com.mfg.mdm.repo.*;
import com.mfg.security.config.CurrentUser;
import com.mfg.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
/** BOM 版本、子件合法性、审批及当前版本切换。 */
@Service @RequiredArgsConstructor public class BomService{
 private final BomRepository bomRepo; private final BomLineRepository lineRepo; private final MaterialRepository materialRepo; private final MasterDataService masterDataService;
 @Transactional public Bom create(BomCommand c){if(bomRepo.existsByBomCodeAndBomVersion(c.bomCode(),version(c)))throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS,"BOM 编码与版本已存在："+c.bomCode());Bom b=new Bom();copyHeader(c,b);b.setCreatedBy(CurrentUser.usernameOrSystem());b=masterDataService.create(b,bomRepo);saveLines(b,c.lines());return detail(b.getId());}
 @Transactional public Bom update(Long id,BomCommand c){Bom b=load(id);if(!b.statusEnum().isEditable())throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"BOM 已发布或审批中，不能直接改；请通过工程变更后重新提交");copyHeader(c,b);masterDataService.update(b,bomRepo,c.changeReason());lineRepo.deleteByBomId(id);saveLines(b,c.lines());return detail(id);}
 @Transactional public WfInstance submit(Long id){Bom b=load(id);if(lineRepo.findByBomIdOrderByLineNoAsc(id).isEmpty())throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"BOM 至少应包含一条用料明细");return masterDataService.submitForApproval(b,bomRepo);}
 @Transactional(readOnly=true) public Bom detail(Long id){Bom b=load(id);b.setLines(lineRepo.findByBomIdOrderByLineNoAsc(id));return b;}
 @Transactional public void makeCurrent(Bom approved){bomRepo.findCurrentByProduct(approved.getProductCode()).ifPresent(old->{if(!old.getId().equals(approved.getId())){old.setCurrent(false);bomRepo.save(old);}});approved.setCurrent(true);bomRepo.save(approved);}
 private Bom load(Long id){return bomRepo.findById(id).orElseThrow(()->BizException.notFound("BOM",id));} private String version(BomCommand c){return c.bomVersion()==null||c.bomVersion().isBlank()?"V1.0":c.bomVersion();}
 private void copyHeader(BomCommand c,Bom b){b.setBomCode(c.bomCode());b.setBomName(c.bomName());b.setProductCode(c.productCode());b.setBomType(c.bomType()==null||c.bomType().isBlank()?"MBOM":c.bomType());b.setBomVersion(version(c));b.setEffectiveDate(c.effectiveDate()==null?LocalDate.now():c.effectiveDate());b.setExpireDate(c.expireDate());b.setBaseQty(c.baseQty()==null?BigDecimal.ONE:c.baseQty());b.setBaseUnitCode(c.baseUnitCode());b.setChangeReason(c.changeReason());b.setChangeEcnNo(c.changeEcnNo());}
 private void saveLines(Bom b,List<BomCommand.BomLineCommand> cs){for(var c:cs){Material m=materialRepo.findByMaterialCode(c.childMaterialCode()).orElseThrow(()->BizException.of(ErrorCode.MASTER_DATA_NOT_FOUND,"子件物料不存在："+c.childMaterialCode()));masterDataService.assertConsumable(m,"BOM 用料明细");BomLine l=new BomLine();l.setBomId(b.getId());l.setLineNo(c.lineNo());l.setChildMaterialId(m.getId());l.setChildMaterialCode(m.getMaterialCode());l.setChildMaterialName(m.getMaterialName());l.setQtyPer(c.qtyPer());l.setUnitCode(c.unitCode());l.setScrapRate(c.scrapRate()==null?BigDecimal.ZERO:c.scrapRate());l.setSeqNo(c.seqNo()==null?c.lineNo():c.seqNo());l.setKeyMaterial(Boolean.TRUE.equals(c.keyMaterial()));l.setPositionDesc(c.positionDesc());l.setSubstituteGroup(c.substituteGroup());l.setRemark(c.remark());lineRepo.save(l);}}
}
