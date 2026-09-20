package com.mfg.security.service;

import com.mfg.security.entity.SysLoginLog;
import com.mfg.security.entity.SysUser;
import com.mfg.security.repo.SysLoginLogRepository;
import com.mfg.security.repo.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginAuditService {
    private static final int LOCK_THRESHOLD = 5;
    private final SysLoginLogRepository logs;
    private final SysUserRepository users;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeeded(String username, String realName, String ip, String agent) {
        save(username, realName, "SUCCESS", null, ip, agent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String username, String realName, String reason, String ip, String agent, boolean countFailure) {
        if (countFailure) {
            users.findByUsernameAndDeletedFalse(username).ifPresent(user -> {
                int count = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
                user.setFailedLoginCount(count);
                if (count >= LOCK_THRESHOLD) user.setLocked(true);
                users.save(user);
            });
        }
        save(username, realName, "FAILED", reason, ip, agent);
    }

    private void save(String username, String realName, String status, String reason, String ip, String agent) {
        SysLoginLog item = new SysLoginLog();
        item.setUsername(username);
        item.setRealName(realName);
        item.setLoginStatus(status);
        item.setFailReason(reason);
        item.setIpAddress(ip);
        item.setUserAgent(agent == null ? null : agent.substring(0, Math.min(500, agent.length())));
        item.setLoginAt(LocalDateTime.now());
        logs.save(item);
    }
}
