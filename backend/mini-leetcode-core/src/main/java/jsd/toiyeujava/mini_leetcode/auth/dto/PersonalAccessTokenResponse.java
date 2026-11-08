package jsd.toiyeujava.mini_leetcode.auth.dto;

import jsd.toiyeujava.mini_leetcode.auth.entity.PersonalAccessToken;

import java.time.Instant;

/** Thông tin token (không bao giờ chứa token gốc hay hash). */
public record PersonalAccessTokenResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt,
        boolean revoked
) {
    public static PersonalAccessTokenResponse fromEntity(PersonalAccessToken t) {
        return new PersonalAccessTokenResponse(t.getId(), t.getName(), t.getCreatedAt(), t.getExpiresAt(),
                t.getLastUsedAt(), t.isRevoked());
    }
}
