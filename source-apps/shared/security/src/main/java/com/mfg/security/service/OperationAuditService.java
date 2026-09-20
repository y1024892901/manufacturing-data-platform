package com.mfg.security.service;

import com.mfg.security.entity.SysAuditLog;
import com.mfg.security.repo.SysAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationAuditService {
    private final SysAuditLogRepository logs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, String realName, String system, String action,
                       String objectType, String objectId, String objectName, String result, String ip) {
        SysAuditLog item = new SysAuditLog();
        item.setOperator(username); item.setOperatorName(realName); item.setSystemCode(system);
        item.setAction(action); item.setObjectType(objectType); item.setObjectId(objectId);
        item.setObjectName(objectName); item.setAfterValue(result); item.setIpAddress(ip);
        item.setOperatedAt(LocalDateTime.now()); logs.save(item);
    }
}
