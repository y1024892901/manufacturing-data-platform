package com.mfg.security.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.dto.DemoAccount;
import com.mfg.security.dto.LoginRequest;
import com.mfg.security.dto.LoginResponse;
import com.mfg.security.entity.SysDept;
import com.mfg.security.entity.SysRole;
import com.mfg.security.entity.SysUser;
import com.mfg.security.jwt.JwtTokenProvider;
import com.mfg.security.repo.SysDeptRepository;
import com.mfg.security.repo.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository userRepository;
    private final SysDeptRepository deptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * 登录。
     *
     * <p>失败时统一返回"用户名或密码错误"，不区分是账号不存在还是密码错，
     * 避免账号枚举。但会记录日志便于排障。
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        SysUser user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> {
                    log.warn("登录失败，账号不存在: {}", req.username());
                    return BizException.of(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
                });

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "账号已停用，请联系管理员");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            log.warn("登录失败，密码错误: {}", req.username());
            throw BizException.of(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());

        List<String> roles = user.getRoles().stream()
                .map(SysRole::getRoleCode).sorted().toList();
        List<String> systems = user.getSystems().stream().sorted().toList();

        String token = tokenProvider.generate(
                user.getUsername(), user.getRealName(), roles, systems);

        log.info("登录成功: {} ({}) 角色={} 可访问系统={}",
                user.getRealName(), user.getUsername(), roles, systems);

        return new LoginResponse(
                token,
                tokenProvider.getExpireMinutes(),
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptCode(),
                deptName(user.getDeptCode()),
                user.getPositionName(),
                roles,
                user.getRoles().stream().map(SysRole::getRoleName).sorted().toList(),
                systems,
                user.getRoles().stream()
                        .flatMap(r -> r.permissionCodes().stream())
                        .distinct().sorted().toList());
    }

    /**
     * 演示账号列表，按部门分组排序。
     *
     * <p>登录页用它渲染「一键切换身份」面板——演示时切换不同岗位的角色，
     * 直观展示权限与审批的差异。
     */
    @Transactional(readOnly = true)
    public List<DemoAccount> demoAccounts() {
        Map<String, String> deptNames = deptNameMap();
        return userRepository.findByDemoAccountTrueAndEnabledTrueOrderByIdAsc().stream()
                .map(u -> new DemoAccount(
                        u.getUsername(),
                        u.getRealName(),
                        u.getDeptCode(),
                        deptNames.getOrDefault(u.getDeptCode(), u.getDeptCode()),
                        u.getPositionName(),
                        u.getRoles().stream().map(SysRole::getRoleName).sorted().toList(),
                        u.getSystems().stream().sorted().toList()))
                .sorted(Comparator
                        .comparing(DemoAccount::deptCode,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DemoAccount::username))
                .toList();
    }

    private String deptName(String deptCode) {
        if (deptCode == null) {
            return null;
        }
        return deptNameMap().get(deptCode);
    }

    private Map<String, String> deptNameMap() {
        return deptRepository.findByEnabledTrueOrderBySortNoAsc().stream()
                .collect(Collectors.toMap(SysDept::getDeptCode, SysDept::getDeptName,
                        (a, b) -> a));
    }
}
