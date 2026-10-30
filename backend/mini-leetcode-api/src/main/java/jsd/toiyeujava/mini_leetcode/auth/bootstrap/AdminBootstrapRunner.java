package jsd.toiyeujava.mini_leetcode.auth.bootstrap;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tài khoản quản trị đầu tiên khi khởi động.
 *
 * <p>Chỉ chạy khi có {@code app.bootstrap.admin.username} được cấu hình. Nếu
 * tài khoản đã tồn tại thì không động vào — runner này chỉ bootstrap, không
 * phải công cụ quản lý role.
 *
 * <p>Mật khẩu mặc định {@code null}: nếu không cấu hình mật khẩu thì runner bỏ
 * qua, tránh tạo tài khoản admin với mật khẩu đoán được.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:}")
    private String adminUsername;

    @Value("${app.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminUsername == null || adminUsername.isBlank()) {
            log.info("Admin bootstrap skipped: app.bootstrap.admin.username is not configured");
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin bootstrap skipped for user '{}': app.bootstrap.admin.password is not configured",
                    adminUsername);
            return;
        }

        if (userRepository.findByUsername(adminUsername).isPresent()) {
            log.info("Admin bootstrap skipped: user '{}' already exists", adminUsername);
            return;
        }

        String email = (adminEmail == null || adminEmail.isBlank())
                ? adminUsername + "@mini-leetcode.local"
                : adminEmail;

        User admin = User.builder()
                .username(adminUsername)
                .email(email)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(admin);
        log.info("Bootstrapped admin account '{}'", adminUsername);
    }
}
