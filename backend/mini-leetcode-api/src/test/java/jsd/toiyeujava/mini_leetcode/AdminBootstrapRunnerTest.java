package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.auth.bootstrap.AdminBootstrapRunner;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        runner = new AdminBootstrapRunner(userRepository, encoder);
    }

    private void configure(String username, String email, String password) throws Exception {
        var usernameField = AdminBootstrapRunner.class.getDeclaredField("adminUsername");
        usernameField.setAccessible(true);
        usernameField.set(runner, username);

        var emailField = AdminBootstrapRunner.class.getDeclaredField("adminEmail");
        emailField.setAccessible(true);
        emailField.set(runner, email);

        var passwordField = AdminBootstrapRunner.class.getDeclaredField("adminPassword");
        passwordField.setAccessible(true);
        passwordField.set(runner, password);
    }

    @Test
    @DisplayName("run: creates an ADMIN account when configured and the user does not exist")
    void run_createsAdminAccount() throws Exception {
        configure("root", "root@example.com", "Password123!");
        when(userRepository.findByUsername("root")).thenReturn(Optional.empty());

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("root");
        assertThat(saved.getEmail()).isEqualTo("root@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getPasswordHash()).isNotEqualTo("Password123!");
    }

    @Test
    @DisplayName("run: does nothing when the username is already taken")
    void run_skipsExistingUser() throws Exception {
        configure("root", "root@example.com", "Password123!");
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(User.builder().id(1L).build()));

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("run: skips when no username is configured")
    void run_skipsWhenNotConfigured() throws Exception {
        configure("", "", "");

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("run: skips when a username is set but no password is configured")
    void run_skipsWithoutPassword() throws Exception {
        configure("root", "root@example.com", "");

        runner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
    }
}
