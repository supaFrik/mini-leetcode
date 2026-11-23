package jsd.toiyeujava.mini_leetcode.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.auth.permission.RolePermissionService;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.entity.Permission;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/** Bảng quyền theo role: /admin/permissions chỉ ADMIN, /auth/me/permissions cho user đang đăng nhập (SecurityConfig). */
@RestController
@Tag(name = "Permissions", description = "Role permissions (ADMIN only) and the current user's permissions")
public class PermissionController {

    private final RolePermissionService service;

    public PermissionController(RolePermissionService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/permissions")
    @Operation(summary = "Permissions of every role (ADMIN only)")
    public Map<Role, Set<Permission>> all() {
        return service.all();
    }

    @PutMapping("/api/v1/admin/permissions")
    @Operation(summary = "Replace INSTRUCTOR permissions (ADMIN only); USER, ADMIN or SYSTEM_MANAGE return 400")
    public Map<Role, Set<Permission>> update(@RequestBody Map<Role, Set<Permission>> changes) {
        return service.update(changes);
    }

    @GetMapping("/api/v1/auth/me/permissions")
    @Operation(summary = "Permissions of the signed-in user")
    public Set<Permission> mine(@AuthenticationPrincipal UserPrincipal user) {
        return service.forRole(user.getRole());
    }
}
