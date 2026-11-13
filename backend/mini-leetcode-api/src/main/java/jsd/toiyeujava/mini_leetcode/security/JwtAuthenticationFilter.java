package jsd.toiyeujava.mini_leetcode.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.auth.security.CustomUserDetailsService;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.auth.security.PatForbiddenException;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.auth.service.PersonalAccessTokenService;
import jsd.toiyeujava.mini_leetcode.exception.GlobalExceptionHandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Endpoint mà Personal Access Token (plugin IDE) được gọi; mọi endpoint khác ⇒ 403 PAT_FORBIDDEN.
     * Khớp mục "Bảo mật PAT" trong specs/study-plan.spec.md.
     */
    private static final List<String[]> PAT_ALLOWLIST = List.of(
            new String[]{"GET", "/api/v1/study-plans"},
            new String[]{"GET", "/api/v1/study-plans/*"},
            new String[]{"GET", "/api/v1/problems/*"},
            new String[]{"GET", "/api/v1/problems/*/starter-template"},
            new String[]{"POST", "/api/v1/problems/*/submit"},
            new String[]{"POST", "/api/v1/problems/*/submit-multipart"},
            new String[]{"POST", "/api/v1/submissions"},
            new String[]{"GET", "/api/v1/submissions/{id:[0-9]+}"} // không khớp /submissions/my
    );
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final PersonalAccessTokenService patService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService,
                                   PersonalAccessTokenService patService, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.patService = patService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (PersonalAccessTokenService.looksLikePat(token)) {
            java.util.Optional<jsd.toiyeujava.mini_leetcode.auth.entity.User> user;
            try {
                user = patService.authenticate(token);
            } catch (Exception e) {
                // Giống nhánh JWT: lỗi hạ tầng không làm vỡ filter chain; request đi tiếp như ẩn danh.
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            if (user.isEmpty()) {
                // Sai / hết hạn / thu hồi ⇒ luôn 401, kể cả endpoint công khai, để plugin biết phải tạo token mới.
                write(response, request, GlobalExceptionHandler.coded(HttpStatus.UNAUTHORIZED, "Authentication Failed",
                        "unauthorized", "Personal access token is invalid, expired or revoked", "PAT_INVALID"));
                return;
            }
            if (!isAllowedForPat(request)) {
                write(response, request, GlobalExceptionHandler.coded(HttpStatus.FORBIDDEN, "Forbidden", "forbidden",
                        "This action is only available on the web, not with a personal access token",
                        PatForbiddenException.CODE));
                return;
            }
            authenticate(new UserPrincipal(user.get(), true), request);
        } else if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                var userDetails = userDetailsService.loadUserByUsername(username);
                if (userDetails.isAccountNonLocked() && userDetails.isEnabled()) {
                    authenticate(userDetails, request);
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(UserDetails userDetails, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static boolean isAllowedForPat(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PAT_ALLOWLIST.stream()
                .anyMatch(rule -> rule[0].equals(request.getMethod()) && PATHS.match(rule[1], path));
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ProblemDetail problem) throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
