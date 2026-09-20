package com.mfg.security.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.security.dto.*;
import com.mfg.security.entity.SysDept;
import com.mfg.security.entity.SysRole;
import com.mfg.security.entity.SysPermission;
import com.mfg.security.service.PlatformAdminService;
import com.mfg.security.repo.SysLoginLogRepository;
import com.mfg.security.repo.SysAuditLogRepository;
import org.springframework.data.domain.PageRequest;
import com.mfg.security.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final UserAdminService service;
    private final PlatformAdminService platform;
    private final SysLoginLogRepository loginLogs;
    private final SysAuditLogRepository auditLogs;

    @GetMapping("/users") public ApiResponse<Page<AdminUserView>> users(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(service.page(keyword, page, size)); }
    @PostMapping("/users") public ApiResponse<AdminUserView> create(@Valid @RequestBody AdminUserCommand command) { return ApiResponse.ok(service.create(command)); }
    @GetMapping("/users/export") public ResponseEntity<byte[]> exportUsers() { return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.csv").contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(service.exportCsv()); }
    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<?> importUsers(@RequestPart("file") MultipartFile file) { return ApiResponse.ok(service.importCsv(file)); }
    @GetMapping("/users/{id}") public ApiResponse<AdminUserView> detail(@PathVariable Long id) { return ApiResponse.ok(service.detail(id)); }
    @PutMapping("/users/{id}") public ApiResponse<AdminUserView> update(@PathVariable Long id, @Valid @RequestBody AdminUserCommand command) { return ApiResponse.ok(service.update(id, command)); }
    @DeleteMapping("/users/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
    @PostMapping("/users/{id}/enable") public ApiResponse<AdminUserView> enable(@PathVariable Long id) { return ApiResponse.ok(service.setEnabled(id, true)); }
    @PostMapping("/users/{id}/disable") public ApiResponse<AdminUserView> disable(@PathVariable Long id) { return ApiResponse.ok(service.setEnabled(id, false)); }
    @PostMapping("/users/{id}/lock") public ApiResponse<AdminUserView> lock(@PathVariable Long id) { return ApiResponse.ok(service.setLocked(id, true)); }
    @PostMapping("/users/{id}/unlock") public ApiResponse<AdminUserView> unlock(@PathVariable Long id) { return ApiResponse.ok(service.setLocked(id, false)); }
    @PostMapping("/users/{id}/reset-password") public ApiResponse<Void> reset(@PathVariable Long id, @Valid @RequestBody PasswordResetCommand command) { service.resetPassword(id, command.password()); return ApiResponse.ok(); }
    @PutMapping("/users/{id}/roles") public ApiResponse<AdminUserView> setRoles(@PathVariable Long id, @Valid @RequestBody IdSetCommand command) { return ApiResponse.ok(service.setRoles(id, command.ids())); }
    @PutMapping("/users/{id}/systems") public ApiResponse<AdminUserView> setSystems(@PathVariable Long id, @Valid @RequestBody SystemSetCommand command) { return ApiResponse.ok(service.setSystems(id, command.systems())); }
    @PutMapping("/users/{id}/data-scope") public ApiResponse<AdminUserView> setDataScope(@PathVariable Long id, @Valid @RequestBody DataScopeCommand command) { return ApiResponse.ok(service.setDataScope(id, command.scopeType(), command.values())); }
    @GetMapping("/roles") public ApiResponse<Page<SysRole>> roles(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(platform.rolePage(page, size)); }
    @GetMapping("/role-options") public ApiResponse<List<SysRole>> roleOptions() { return ApiResponse.ok(platform.roles()); }
    @PostMapping("/roles") public ApiResponse<SysRole> createRole(@Valid @RequestBody RoleCommand command) { return ApiResponse.ok(platform.createRole(command)); }
    @PutMapping("/roles/{id}") public ApiResponse<SysRole> updateRole(@PathVariable Long id, @Valid @RequestBody RoleCommand command) { return ApiResponse.ok(platform.updateRole(id, command)); }
    @DeleteMapping("/roles/{id}") public ApiResponse<Void> deleteRole(@PathVariable Long id) { platform.deleteRole(id); return ApiResponse.ok(); }
    @PutMapping("/roles/{id}/permissions") public ApiResponse<SysRole> setRolePermissions(@PathVariable Long id, @Valid @RequestBody IdSetCommand command) { return ApiResponse.ok(platform.setRolePermissions(id, command.ids())); }
    @GetMapping("/permissions") public ApiResponse<List<SysPermission>> permissions(@RequestParam(required = false) String system) { return ApiResponse.ok(platform.permissions(system)); }

    @GetMapping("/departments") public ApiResponse<Page<SysDept>> departments(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(platform.departmentPage(page, size)); }
    @GetMapping("/department-options") public ApiResponse<List<SysDept>> departmentOptions() { return ApiResponse.ok(platform.departments()); }
    @PostMapping("/departments") public ApiResponse<SysDept> createDepartment(@Valid @RequestBody DepartmentCommand command) { return ApiResponse.ok(platform.createDepartment(command)); }
    @PutMapping("/departments/{id}") public ApiResponse<SysDept> updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentCommand command) { return ApiResponse.ok(platform.updateDepartment(id, command)); }
    @DeleteMapping("/departments/{id}") public ApiResponse<Void> deleteDepartment(@PathVariable Long id) { platform.deleteDepartment(id); return ApiResponse.ok(); }
    @GetMapping("/login-logs") public ApiResponse<?> loginLogs(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(loginLogs.findAllByOrderByLoginAtDesc(PageRequest.of(Math.max(0, page - 1), Math.min(200, Math.max(1, size))))); }
    @GetMapping("/audit-logs") public ApiResponse<?> auditLogs(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return ApiResponse.ok(auditLogs.findAllByOrderByOperatedAtDesc(PageRequest.of(Math.max(0, page - 1), Math.min(200, Math.max(1, size))))); }
}
