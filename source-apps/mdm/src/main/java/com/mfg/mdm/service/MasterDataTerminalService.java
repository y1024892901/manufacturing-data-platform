package com.mfg.mdm.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.mdm.domain.MasterDataEntity;
import com.mfg.mdm.domain.MasterDataStatus;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.service.OperationAuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** 四类工程主数据共用的停用模板。审批中不可停用，防止回调重新发布。 */
@Service
@RequiredArgsConstructor
public class MasterDataTerminalService {
    private final EntityManager entities;
    private final OperationAuditService audit;
    private final MdmOutboxService outbox;

    @Transactional
    public <T extends MasterDataEntity> T disable(Class<T> type, Long id) {
        T entity = entities.find(type, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) throw BizException.notFound("主数据", id);
        if (entity.statusEnum() != MasterDataStatus.PUBLISHED)
            throw BizException.of(ErrorCode.MASTER_DATA_INVALID_STATE, "只有已发布且不在审批中的主数据可停用");
        String operator = CurrentUser.usernameOrSystem();
        entity.setStatus(MasterDataStatus.DISABLED.name());
        entity.setUpdatedBy(operator);
        entity.setUpdatedAt(LocalDateTime.now());
        audit.recordTransition(operator, "mdm", "DISABLE", entity.bizType().name(), String.valueOf(id),
                entity.getName(), "{\"status\":\"PUBLISHED\"}", "{\"status\":\"DISABLED\"}");
        outbox.enqueueDisabled(entity);
        return entity;
    }
}
