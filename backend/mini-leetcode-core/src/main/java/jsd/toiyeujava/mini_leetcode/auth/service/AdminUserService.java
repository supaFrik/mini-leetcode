package jsd.toiyeujava.mini_leetcode.auth.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.auth.dto.UserDto;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.RefreshTokenRepository;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.exception.DuplicateResourceException;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.exception.StateConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Quản trị tài khoản người dùng (ADMIN). */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    /** Mọi trường đều tuỳ chọn: {@code null} = giữ nguyên. */
    public record UpdateUserRequest(
            @Size(min = 3, max = 50) String username,
            @Email @Size(max = 100) String email,
            Role role,
            UserStatus status,
            @Size(min = 8, max = 100) String password
    ) {
    }

    @Transactional(readOnly = true)
    public Page<UserDto> listUsers(String search, Pageable pageable) {
        Page<User> page = StringUtils.hasText(search)
                ? userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(search.trim(), search.trim(), pageable)
                : userRepository.findAll(pageable);
        return page.map(AdminUserService::toDto);
    }

    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest req, Long actorId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Không cho admin tự khoá / tự hạ quyền mình — tránh mất quyền quản trị cuối cùng.
        if (id.equals(actorId)) {
            if (req.role() != null && req.role() != user.getRole()) {
                throw new StateConflictException("You cannot change your own role");
            }
            if (req.status() == UserStatus.BLOCKED) {
                throw new StateConflictException("You cannot block your own account");
            }
        }

        boolean revokeSessions = false;
        if (StringUtils.hasText(req.username()) && !req.username().trim().equals(user.getUsername())) {
            String username = req.username().trim();
            if (userRepository.existsByUsername(username)) {
                throw new DuplicateResourceException("Username already taken: " + username);
            }
            user.setUsername(username);
            revokeSessions = true; // JWT mang username cũ
        }
        if (StringUtils.hasText(req.email()) && !req.email().trim().equalsIgnoreCase(user.getEmail())) {
            String email = req.email().trim();
            if (userRepository.existsByEmail(email)) {
                throw new DuplicateResourceException("Email already in use: " + email);
            }
            user.setEmail(email);
        }
        if (req.role() != null) {
            user.setRole(req.role());
        }
        if (req.status() != null && req.status() != user.getStatus()) {
            user.setStatus(req.status());
            revokeSessions |= req.status() == UserStatus.BLOCKED;
        }
        if (StringUtils.hasText(req.password())) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            revokeSessions = true;
        }

        user = userRepository.save(user);
        if (revokeSessions) {
            refreshTokenRepository.revokeAllByUser(user);
        }
        return toDto(user);
    }

    private static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.getStatus());
    }
}
