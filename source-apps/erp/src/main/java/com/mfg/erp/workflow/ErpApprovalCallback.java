package com.mfg.erp.workflow;

import com.mfg.erp.service.ErpP2Service;
import com.mfg.workflow.callback.ApprovalCallback;
import com.mfg.workflow.entity.WfInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ErpApprovalCallback implements ApprovalCallback {
    private final ErpP2Service service;

    @Override
    public boolean supports(String bizType) {
        return "CREDIT_EXCEPTION".equals(bizType);
    }

    @Override
    public void onApproved(WfInstance instance) {
        service.creditApproval(instance.getBizId(), true);
    }

    @Override
    public void onRejected(WfInstance instance, String reason) {
        service.creditApproval(instance.getBizId(), false);
    }
}
