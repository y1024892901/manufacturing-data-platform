package com.mfg.plm.service;
import com.mfg.common.api.ErrorCode;import com.mfg.common.exception.BizException;import com.mfg.plm.entity.EngineeringChange;import com.mfg.plm.repo.EngineeringChangeRepository;import com.mfg.security.config.CurrentUser;import com.mfg.workflow.dto.StartApprovalRequest;import com.mfg.workflow.entity.WfInstance;import com.mfg.workflow.service.ApprovalEngine;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
/** ECN 生命周期。实施动作只授权创建 MDM BOM 新版本，避免绕开主数据治理。 */
@Service @RequiredArgsConstructor public class EngineeringChangeService{
 private final EngineeringChangeRepository repo;private final ApprovalEngine approvalEngine;
 @Transactional public EngineeringChange create(EngineeringChange e){if(repo.existsByEcnNo(e.getEcnNo()))throw BizException.of(ErrorCode.MASTER_DATA_ALREADY_EXISTS,"ECN 编号已存在："+e.getEcnNo());e.setStatus("DRAFT");return repo.save(e);}
 @Transactional public WfInstance submit(Long id){EngineeringChange e=load(id);if(!"DRAFT".equals(e.getStatus()))throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"只有草稿 ECN 可以提交评审");e.setStatus("REVIEWING");e.setSubmittedBy(CurrentUser.usernameOrSystem());repo.save(e);return approvalEngine.start(new StartApprovalRequest("ECN",e.getId(),e.getEcnNo(),e.getEcnNo()+" · "+e.getEcnTitle(),"{\"productCode\":\""+e.getProductCode()+"\",\"bomCode\":\""+(e.getBomCode()==null?"":e.getBomCode())+"\"}"));}
 @Transactional public EngineeringChange implement(Long id){EngineeringChange e=load(id);if(!"APPROVED".equals(e.getStatus()))throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE,"ECN 未批准，不能实施");e.setStatus("IMPLEMENTED");return repo.save(e);}
 private EngineeringChange load(Long id){return repo.findById(id).orElseThrow(()->BizException.notFound("工程变更单",id));}
}
