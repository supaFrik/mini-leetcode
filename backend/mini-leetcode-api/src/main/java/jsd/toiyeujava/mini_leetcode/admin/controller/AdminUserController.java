package jsd.toiyeujava.mini_leetcode.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.admin.service.AdminUserService;
import jsd.toiyeujava.mini_leetcode.admin.service.AdminUserService.Stats;
import jsd.toiyeujava.mini_leetcode.admin.service.AdminUserService.UserList;
import jsd.toiyeujava.mini_leetcode.admin.service.AdminUserService.UserRow;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Tổng quan và quản lý người dùng. Chỉ ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin users", description = "Dashboard stats and user management (ADMIN only)")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    public record UpdateUserRequest(Role role, UserStatus status) {
    }

    @GetMapping("/stats")
    @Operation(summary = "Site totals, latest submissions and newest users")
    public Stats stats() {
        return service.stats();
    }

    @GetMapping("/users")
    @Operation(summary = "Search users by username or email, newest first")
    public UserList users(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
        return service.list(q, page);
    }

    @PatchMapping("/users/{id}")
    @Operation(summary = "Change a user's role or status; you cannot change your own")
    public UserRow update(@PathVariable Long id, @RequestBody UpdateUserRequest request,
                          @AuthenticationPrincipal UserPrincipal currentUser) {
        return service.update(currentUser.getId(), id, request.role(), request.status());
    }
}
