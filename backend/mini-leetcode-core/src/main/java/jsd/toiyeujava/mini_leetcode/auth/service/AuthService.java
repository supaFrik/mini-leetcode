package jsd.toiyeujava.mini_leetcode.auth.service;

import jsd.toiyeujava.mini_leetcode.auth.dto.*;
import jsd.toiyeujava.mini_leetcode.auth.entity.RefreshToken;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.exception.DuplicateResourceException;
import jsd.toiyeujava.mini_leetcode.exception.ForbiddenOperationException;
import jsd.toiyeujava.mini_leetcode.settings.SiteSettingsService;
import jsd.toiyeujava.mini_leetcode.exception.InvalidTokenException;
import jsd.toiyeujava.mini_leetcode.auth.repository.RefreshTokenRepository;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SiteSettingsService siteSettings;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            SiteSettingsService siteSettings) {
        this.siteSettings = siteSettings;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!siteSettings.registrationOpen()) {
            throw new ForbiddenOperationException("New sign-ups are currently closed");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        // Public self-registration always assigns Role.USER to prevent privilege escalation
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmail(request.usernameOrEmail(), request.usernameOrEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new BadCredentialsException("Account is blocked");
        }

        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = JwtTokenProvider.hashToken(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found or invalid"));

        if (storedToken.isRevoked()) {
            // Token reuse detection (RFC 6819 Section 5.2.2.3): revoke all active tokens for compromised user
            refreshTokenRepository.revokeAllByUser(storedToken.getUser());
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        // Revoke the old refresh token (Token Rotation)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new InvalidTokenException("Account is blocked");
        }

        return createAuthResponse(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = JwtTokenProvider.hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse createAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenString = jwtTokenProvider.generateRefreshTokenString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(JwtTokenProvider.hashToken(refreshTokenString))
                .expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        UserDto userDto = new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus()
        );

        return new AuthResponse(
                accessToken,
                refreshTokenString,
                jwtTokenProvider.getAccessTokenExpirationMs(),
                userDto
        );
    }
}
