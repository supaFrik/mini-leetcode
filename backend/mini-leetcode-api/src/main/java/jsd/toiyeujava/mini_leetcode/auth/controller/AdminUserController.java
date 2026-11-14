package jsd.toiyeujava.mini_leetcode.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.dto.UserDto;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.auth.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Users", description = "Admin-only user management: role, status, email, username, password reset")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List / search users")
    public ResponseEntity<Page<UserDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id"));
        return ResponseEntity.ok(adminUserService.listUsers(search, pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user", description = "Every field is optional. Blocking, password reset and rename revoke the user's refresh tokens.")
    public ResponseEntity<UserDto> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserService.UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(adminUserService.updateUser(id, request, actor.getId()));
    }
}
