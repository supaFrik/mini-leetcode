package jsd.toiyeujava.mini_leetcode.security;

import jsd.toiyeujava.mini_leetcode.entity.Permission;
import jsd.toiyeujava.mini_leetcode.security.CustomAccessDeniedHandler;
import jsd.toiyeujava.mini_leetcode.security.CustomAuthenticationEntryPoint;
import jsd.toiyeujava.mini_leetcode.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] STAFF_PERMISSIONS = java.util.Arrays.stream(Permission.values())
            .map(Enum::name).toArray(String[]::new);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/me/**").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health", "/actuator/info").permitAll()
                        // Submissions require auth
                        .requestMatchers("/api/v1/submissions/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/problems/*/submit").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/problems/*/submissions/**").authenticated()
                        // Console live: handshake WebSocket công khai, JWT được kiểm tra ở frame "start" đầu tiên
                        // (trình duyệt không gửi được header Authorization khi mở WebSocket).
                        .requestMatchers("/api/v1/console/ws").permitAll()
                        // Soạn bài (grading_config, lời giải mẫu): KHÔNG công khai như GET /api/v1/problems/**
                        // Xoá kỳ thi (gỡ thí sinh /contests/{id}/participants/{uid} là quyền khác).
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/contests/*").hasAuthority("CONTEST_DELETE")
                        // Bảng quyền: luôn theo role ADMIN, để không ai tự cấp quyền cho mình.
                        .requestMatchers("/api/v1/admin/permissions/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/tags/**", "/api/v1/admin/tag-categories/**").hasAuthority("TAG_MANAGE")
                        .requestMatchers("/api/v1/admin/pages/**").hasAuthority("PAGE_MANAGE")
                        .requestMatchers("/api/v1/admin/announcements/**").hasAuthority("ANNOUNCEMENT_SEND")
                        .requestMatchers("/api/v1/admin/submissions/**").hasAuthority("CONTEST_REPORTS")
                        .requestMatchers("/api/v1/admin/stats", "/api/v1/admin/users/**", "/api/v1/admin/settings/**")
                                .hasAuthority("SYSTEM_MANAGE")
                        // Còn lại trong khu quản trị: có ít nhất một quyền quản trị là vào được.
                        .requestMatchers("/api/v1/admin/**", "/api/v1/teaching/**").hasAnyAuthority(STAFF_PERMISSIONS)
                        // Bảng xếp hạng và trang chính sách: đọc công khai.
                        .requestMatchers(HttpMethod.GET, "/api/v1/ranking", "/api/v1/pages/**", "/api/v1/settings/public", "/api/v1/tag-categories").permitAll()
                        // Problems public reads
                        .requestMatchers(HttpMethod.GET, "/api/v1/problems/**").permitAll()
                        // Testcase management (more specific paths must come before general problem routes)
                        .requestMatchers(HttpMethod.POST, "/api/v1/problems/*/test-cases/**").hasAuthority("PROBLEM_EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/problems/*/test-cases/**").hasAuthority("PROBLEM_EDIT")
                        // Problems instructor/admin writes
                        .requestMatchers(HttpMethod.POST, "/api/v1/problems").hasAuthority("PROBLEM_EDIT")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/problems/**").hasAuthority("PROBLEM_EDIT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/problems/**").hasAuthority("PROBLEM_DELETE")
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
