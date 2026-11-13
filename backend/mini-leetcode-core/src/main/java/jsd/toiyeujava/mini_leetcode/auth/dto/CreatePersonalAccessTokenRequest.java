package jsd.toiyeujava.mini_leetcode.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code expiresInDays} bỏ trống ⇒ 90 ngày. */
public record CreatePersonalAccessTokenRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(365) Integer expiresInDays
) {}
