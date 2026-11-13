package jsd.toiyeujava.mini_leetcode.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.auth.dto.CreatePersonalAccessTokenRequest;
import jsd.toiyeujava.mini_leetcode.auth.dto.PersonalAccessTokenResponse;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.auth.service.PersonalAccessTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Quản lý Personal Access Token cho plugin IDE. Chỉ gọi được bằng JWT của web:
 * bộ lọc chặn PAT ở mọi đường dẫn ngoài allowlist, nên token không tự tạo / thu hồi token khác được.
 */
@RestController
@RequestMapping("/api/v1/me/tokens")
@Tag(name = "Personal Access Tokens", description = "Tokens for the IntelliJ plugin")
public class PersonalAccessTokenController {

    private final PersonalAccessTokenService patService;

    public PersonalAccessTokenController(PersonalAccessTokenService patService) {
        this.patService = patService;
    }

    @PostMapping
    @Operation(summary = "Create a token", description = "Returns the raw token exactly once; only its SHA-256 is stored")
    public ResponseEntity<PersonalAccessTokenService.CreatedToken> create(
            @Valid @RequestBody CreatePersonalAccessTokenRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patService.create(currentUser.getId(), request.name(), request.expiresInDays()));
    }

    @GetMapping
    @Operation(summary = "List my tokens")
    public List<PersonalAccessTokenResponse> list(@AuthenticationPrincipal UserPrincipal currentUser) {
        return patService.list(currentUser.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a token")
    public ResponseEntity<Void> revoke(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal currentUser) {
        patService.revoke(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
