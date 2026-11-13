package jsd.toiyeujava.mini_leetcode.auth.service;

import jsd.toiyeujava.mini_leetcode.auth.dto.PersonalAccessTokenResponse;
import jsd.toiyeujava.mini_leetcode.auth.entity.PersonalAccessToken;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.PersonalAccessTokenRepository;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Personal Access Token cho plugin IDE (spec: specs/study-plan.spec.md, mục "Bảo mật PAT").
 * Token gốc chỉ trả về lúc tạo; DB lưu SHA-256 hex. Token đủ entropy (32 byte) nên không cần bcrypt.
 */
@Service
public class PersonalAccessTokenService {

    public static final String PREFIX = "mlc_pat_";
    public static final int DEFAULT_EXPIRES_DAYS = 90;
    public static final int MAX_EXPIRES_DAYS = 365;
    public static final int MAX_ACTIVE_TOKENS = 5;
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);

    private final SecureRandom random = new SecureRandom();
    private final PersonalAccessTokenRepository tokenRepository;
    private final UserRepository userRepository;

    public PersonalAccessTokenService(PersonalAccessTokenRepository tokenRepository, UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    /** Token gốc + thông tin; token gốc không bao giờ lấy lại được sau lần này. */
    public record CreatedToken(String token, PersonalAccessTokenResponse info) {}

    public static boolean looksLikePat(String bearer) {
        return bearer != null && bearer.startsWith(PREFIX);
    }

    @Transactional
    public CreatedToken create(Long userId, String name, Integer expiresInDays) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("Token name is required (max 100 characters)");
        }
        int days = expiresInDays == null ? DEFAULT_EXPIRES_DAYS : expiresInDays;
        if (days < 1 || days > MAX_EXPIRES_DAYS) {
            throw new IllegalArgumentException("expiresInDays must be between 1 and " + MAX_EXPIRES_DAYS);
        }
        Instant now = Instant.now();
        if (tokenRepository.countActiveByUserId(userId, now) >= MAX_ACTIVE_TOKENS) {
            throw new StateConflictException("At most " + MAX_ACTIVE_TOKENS + " active tokens per user; revoke one first");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

        PersonalAccessToken saved = tokenRepository.save(PersonalAccessToken.builder()
                .user(user)
                .name(name.trim())
                .tokenHash(sha256(raw))
                .expiresAt(now.plus(Duration.ofDays(days)))
                .build());
        return new CreatedToken(raw, PersonalAccessTokenResponse.fromEntity(saved));
    }

    @Transactional(readOnly = true)
    public List<PersonalAccessTokenResponse> list(Long userId) {
        return tokenRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PersonalAccessTokenResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void revoke(Long userId, Long tokenId) {
        PersonalAccessToken token = tokenRepository.findById(tokenId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Token not found with id: " + tokenId));
        token.setRevoked(true);
    }

    /** Người dùng của token nếu token hợp lệ, chưa thu hồi, chưa hết hạn và tài khoản đang ACTIVE. */
    @Transactional
    public Optional<User> authenticate(String raw) {
        if (!looksLikePat(raw)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return tokenRepository.findWithUserByTokenHash(sha256(raw))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(now))
                .filter(t -> t.getUser().getStatus() == UserStatus.ACTIVE)
                .map(t -> {
                    if (t.getLastUsedAt() == null || t.getLastUsedAt().isBefore(now.minus(LAST_USED_RESOLUTION))) {
                        t.setLastUsedAt(now);
                    }
                    return t.getUser();
                });
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
