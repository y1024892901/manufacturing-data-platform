package com.mfg.security.service;

import com.mfg.common.api.ErrorCode;
import com.mfg.common.exception.BizException;
import com.mfg.security.dto.DepartmentCommand;
import com.mfg.security.dto.RoleCommand;
import com.mfg.security.entity.SysDept;
import com.mfg.security.entity.SysPermission;
import com.mfg.security.entity.SysRole;
import com.mfg.security.repo.SysDeptRepository;
import com.mfg.security.repo.SysPermissionRepository;
import com.mfg.security.repo.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {
    private final SysRoleRepository roles;
    private final SysPermissionRepository permissions;
    private final SysDeptRepository departments;

    @Transactional(readOnly = true)
    public List<SysRole> roles() { return roles.findAllByOrderBySortNoAsc(); }

    @Transactional(readOnly = true)
    public Page<SysRole> rolePage(int page, int size) { return roles.findAllByOrderBySortNoAsc(PageRequest.of(Math.max(0, page - 1), Math.min(200, Math.max(1, size)))); }

    @Transactional(readOnly = true)
    public List<SysPermission> permissions(String system) {
        return system == null || system.isBlank()
                ? permissions.findAllByOrderBySystemCodeAscSortNoAsc()
                : permissions.findBySystemCodeOrderBySortNoAsc(system.toLowerCase());
    }

    @Transactional
    public SysRole createRole(RoleCommand command) {
        if (roles.findByRoleCode(command.roleCode().trim()).isPresent())
            throw BizException.of(ErrorCode.DUPLICATE_KEY, "角色编码已存在");
        SysRole role = new SysRole();
        role.setRoleCode(command.roleCode().trim().toUpperCase());
        role.setSystem(false);
        applyRole(role, command);
        return roles.save(role);
    }

    @Transactional
    public SysRole updateRole(Long id, RoleCommand command) {
        SysRole role = loadRole(id);
        if (Boolean.TRUE.equals(role.getSystem()) && !role.getRoleCode().equalsIgnoreCase(command.roleCode()))
            throw BizException.conflict("系统角色编码不可修改");
        role.setRoleCode(command.roleCode().trim().toUpperCase());
        applyRole(role, command);
        return roles.save(role);
    }

    @Transactional
    public SysRole setRolePermissions(Long id, Set<Long> ids) {
        SysRole role = loadRole(id);
        Set<SysPermission> selected = new HashSet<>(permissions.findAllById(ids));
        if (selected.size() != ids.size()) throw BizException.of(ErrorCode.PARAM_INVALID, "包含不存在的权限点");
        role.setPermissions(selected);
        return roles.save(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        SysRole role = loadRole(id);
        if (Boolean.TRUE.equals(role.getSystem())) throw BizException.conflict("系统内置角色不可删除");
        roles.delete(role);
    }

    @Transactional(readOnly = true)
    public List<SysDept> departments() { return departments.findAll(); }

    @Transactional(readOnly = true)
    public Page<SysDept> departmentPage(int page, int size) { return departments.findAllByOrderBySortNoAsc(PageRequest.of(Math.max(0, page - 1), Math.min(200, Math.max(1, size)))); }

    @Transactional
    public SysDept createDepartment(DepartmentCommand command) {
        if (departments.findByDeptCode(command.deptCode().trim()).isPresent())
            throw BizException.of(ErrorCode.DUPLICATE_KEY, "部门编码已存在");
        SysDept dept = new SysDept();
        dept.setDeptCode(command.deptCode().trim().toUpperCase());
        applyDepartment(dept, command);
        return departments.save(dept);
    }

    @Transactional
    public SysDept updateDepartment(Long id, DepartmentCommand command) {
        SysDept dept = departments.findById(id).orElseThrow(() -> BizException.notFound("部门", id));
        if (!dept.getDeptCode().equalsIgnoreCase(command.deptCode()))
            throw BizException.conflict("部门编码不可修改");
        applyDepartment(dept, command);
        return departments.save(dept);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        SysDept dept = departments.findById(id).orElseThrow(() -> BizException.notFound("部门", id));
        dept.setEnabled(false);
        departments.save(dept);
    }

    private void applyRole(SysRole role, RoleCommand command) {
        role.setRoleName(command.roleName().trim());
        role.setDescription(command.description());
        role.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        if (command.permissionIds() != null) setPermissions(role, command.permissionIds());
    }

    private void setPermissions(SysRole role, Set<Long> ids) {
        Set<SysPermission> selected = new HashSet<>(permissions.findAllById(ids));
        if (selected.size() != ids.size()) throw BizException.of(ErrorCode.PARAM_INVALID, "包含不存在的权限点");
        role.setPermissions(selected);
    }

    private void applyDepartment(SysDept dept, DepartmentCommand command) {
        if (command.parentCode() != null && !command.parentCode().isBlank()
                && departments.findByDeptCode(command.parentCode()).isEmpty())
            throw BizException.of(ErrorCode.PARAM_INVALID, "上级部门不存在");
        dept.setDeptName(command.deptName().trim());
        dept.setParentCode(command.parentCode());
        dept.setDeptLevel(command.deptLevel() == null ? 1 : command.deptLevel());
        dept.setManagerUser(command.managerUser());
        dept.setSortNo(command.sortNo() == null ? 0 : command.sortNo());
        dept.setEnabled(command.enabled() == null || command.enabled());
    }

    private SysRole loadRole(Long id) {
        return roles.findById(id).orElseThrow(() -> BizException.notFound("角色", id));
    }
}
