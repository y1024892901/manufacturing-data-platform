package com.mfg.plm.workflow;
import com.mfg.plm.repo.EngineeringChangeRepository;import com.mfg.workflow.callback.ApprovalCallback;import com.mfg.workflow.entity.WfInstance;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;
/** 审批通过才批准 ECN；驳回/撤回回到草稿以便工程师修订后再次提交。 */
@Component @RequiredArgsConstructor public class EngineeringChangeApprovalCallback implements ApprovalCallback{
 private final EngineeringChangeRepository repo;
 @Override public boolean supports(String bizType){return "ECN".equals(bizType);}
 @Override public void onApproved(WfInstance i){repo.findById(i.getBizId()).ifPresent(e->{e.setStatus("APPROVED");e.setApprovedBy(i.getSubmitter());e.setApprovedAt(java.time.LocalDateTime.now());repo.save(e);});}
 @Override public void onRejected(WfInstance i,String reason){repo.findById(i.getBizId()).ifPresent(e->{e.setStatus("DRAFT");e.setChangeReason((e.getChangeReason()==null?"":e.getChangeReason()+"\n")+"审批退回："+reason);repo.save(e);});}
}
