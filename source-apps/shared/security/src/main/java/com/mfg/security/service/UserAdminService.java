package com.mfg.security.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.config.CurrentUser;
import com.mfg.security.dto.AdminUserCommand;
import com.mfg.security.dto.AdminUserView;
import com.mfg.security.entity.SysRole;
import com.mfg.security.entity.SysUser;
import com.mfg.security.repo.SysDeptRepository;
import com.mfg.security.repo.SysRoleRepository;
import com.mfg.security.repo.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class UserAdminService {
    private static final Set<String> SYSTEMS = Set.of("mdm", "crm", "erp", "plm", "srm", "wms", "mes", "qms", "eam", "energy");
    private final SysUserRepository users;
    private final SysRoleRepository roles;
    private final SysDeptRepository departments;
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public Page<AdminUserView> page(String keyword, int page, int size) {
        return users.searchActive(keyword, PageRequest.of(Math.max(0, page - 1), Math.min(200, size))).map(AdminUserView::from);
    }

    @Transactional(readOnly = true)
    public AdminUserView detail(Long id) { return AdminUserView.from(load(id)); }

    @Transactional
    public AdminUserView create(AdminUserCommand command) {
        if (users.existsByUsername(command.username())) throw BizException.of(ErrorCode.DUPLICATE_KEY, "用户名已存在");
        if (command.initialPassword() == null || command.initialPassword().length() < 8) throw BizException.of(ErrorCode.PARAM_INVALID, "初始密码至少8位");
        SysUser user = new SysUser(); user.setUsername(command.username().trim()); user.setPasswordHash(encoder.encode(command.initialPassword()));
        user.setEnabled(true); user.setLocked(false); user.setDeleted(false); user.setFailedLoginCount(0); user.setPasswordChangedAt(LocalDateTime.now());
        applyProfile(user, command); return AdminUserView.from(users.save(user));
    }

    @Transactional
    public AdminUserView update(Long id, AdminUserCommand command) {
        SysUser user = load(id); applyProfile(user, command); return AdminUserView.from(users.save(user));
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = load(id); if (user.getUsername().equals(CurrentUser.get().getUsername())) throw BizException.conflict("不能删除当前登录账号");
        user.setDeleted(true); user.setEnabled(false); user.setDeletedAt(LocalDateTime.now()); users.save(user);
    }

    @Transactional public AdminUserView setEnabled(Long id, boolean enabled) { SysUser user = load(id); user.setEnabled(enabled); if (enabled) user.setFailedLoginCount(0); return AdminUserView.from(users.save(user)); }
    @Transactional public AdminUserView setLocked(Long id, boolean locked) { SysUser user = load(id); user.setLocked(locked); if (!locked) user.setFailedLoginCount(0); return AdminUserView.from(users.save(user)); }
    @Transactional public void resetPassword(Long id, String password) { SysUser user = load(id); user.setPasswordHash(encoder.encode(password)); user.setPasswordChangedAt(LocalDateTime.now()); user.setFailedLoginCount(0); user.setLocked(false); users.save(user); }
    @Transactional public AdminUserView setRoles(Long id, Set<Long> roleIds) { SysUser user = load(id); user.setRoles(new HashSet<>(roles.findAllById(roleIds))); if (user.getRoles().size() != roleIds.size()) throw BizException.of(ErrorCode.PARAM_INVALID, "包含不存在的角色"); return AdminUserView.from(users.save(user)); }
    @Transactional public AdminUserView setSystems(Long id, Set<String> requested) { validateSystems(requested); SysUser user = load(id); user.setSystems(new HashSet<>(requested)); return AdminUserView.from(users.save(user)); }
    @Transactional public AdminUserView setDataScope(Long id, String type, Set<String> values) { if (!Set.of("ROLE","ALL","DEPT","SELF","NONE").contains(type)) throw BizException.of(ErrorCode.PARAM_INVALID, "未知数据范围类型"); SysUser user=load(id); user.setDataScopeType(type); user.setDataScopeValue(values==null||values.isEmpty()?null:String.join(",",values)); return AdminUserView.from(users.save(user)); }

    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFFusername,realName,empCode,deptCode,positionName,email,mobile,enabled,roles,systems\r\n");
        users.findByDeletedFalseOrderByIdAsc().forEach(user -> csv.append(String.join(",",
                value(user.getUsername()), value(user.getRealName()), value(user.getEmpCode()), value(user.getDeptCode()),
                value(user.getPositionName()), value(user.getEmail()), value(user.getMobile()),
                String.valueOf(Boolean.TRUE.equals(user.getEnabled())), value(String.join("|", user.roleCodes())),
                value(String.join("|", user.getSystems())))).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public Map<String, Object> importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "请选择 CSV 文件");
        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        try {
            String[] lines = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "").split("\\R");
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isBlank()) continue;
                String[] c = lines[i].split(",", -1);
                if (c.length < 10) { errors.add("第" + (i + 1) + "行列数不足"); continue; }
                if (users.existsByUsername(c[0].trim())) { skipped++; continue; }
                Set<SysRole> selectedRoles = new HashSet<>();
                for (String code : splitSet(c[8])) roles.findByRoleCode(code).ifPresent(selectedRoles::add);
                Set<String> selectedSystems = splitSet(c[9]); validateSystems(selectedSystems);
                SysUser user = new SysUser(); user.setUsername(c[0].trim()); user.setPasswordHash(encoder.encode("Test@123456"));
                user.setRealName(c[1].trim()); user.setEmpCode(blank(c[2])); user.setDeptCode(blank(c[3]));
                user.setPositionName(blank(c[4])); user.setEmail(blank(c[5])); user.setMobile(blank(c[6]));
                user.setEnabled(Boolean.parseBoolean(c[7])); user.setLocked(false); user.setDeleted(false);
                user.setDemoAccount(true); user.setFailedLoginCount(0); user.setPasswordChangedAt(LocalDateTime.now());
                user.setRoles(selectedRoles); user.setSystems(selectedSystems); users.save(user); created++;
            }
        } catch (java.io.IOException ex) { throw BizException.of(ErrorCode.PARAM_INVALID, "CSV 文件读取失败"); }
        return Map.of("created", created, "skipped", skipped, "errors", errors, "initialPassword", "Test@123456");
    }

    private void applyProfile(SysUser user, AdminUserCommand command) {
        if (command.deptCode() != null && !command.deptCode().isBlank() && departments.findByDeptCode(command.deptCode()).isEmpty()) throw BizException.of(ErrorCode.PARAM_INVALID, "部门编码不存在");
        user.setRealName(command.realName().trim()); user.setEmpCode(command.empCode()); user.setDeptCode(command.deptCode()); user.setPositionName(command.positionName()); user.setEmail(command.email()); user.setMobile(command.mobile()); user.setDemoAccount(command.demoAccount() == null || command.demoAccount());
        if (command.roleIds() != null) { Set<SysRole> selected = new HashSet<>(roles.findAllById(command.roleIds())); if (selected.size() != command.roleIds().size()) throw BizException.of(ErrorCode.PARAM_INVALID, "包含不存在的角色"); user.setRoles(selected); }
        if (command.systems() != null) { validateSystems(command.systems()); user.setSystems(new HashSet<>(command.systems())); }
    }
    private void validateSystems(Set<String> requested) { if (!SYSTEMS.containsAll(requested)) throw BizException.of(ErrorCode.PARAM_INVALID, "包含未知系统编码"); }
    private SysUser load(Long id) { return users.findById(id).filter(u -> !Boolean.TRUE.equals(u.getDeleted())).orElseThrow(() -> BizException.notFound("用户", id)); }
    private String value(String value) { return value == null ? "" : value.replace(',', '，').replace('\r', ' ').replace('\n', ' '); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Set<String> splitSet(String value) { return value == null || value.isBlank() ? new HashSet<>() : new HashSet<>(List.of(value.split("\\|"))); }
}
