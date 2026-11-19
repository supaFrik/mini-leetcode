package jsd.toiyeujava.mini_leetcode.ownership;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.contest.repository.ContestRepository;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.exception.ForbiddenOperationException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Giảng viên chỉ được sửa bài/kỳ thi do chính mình tạo; ADMIN sửa được mọi thứ.
 * Chỉ chặn request ghi (không phải GET); clone bài của người khác vẫn được phép vì tạo bản sao mới.
 */
@Component
public class OwnershipInterceptor implements HandlerInterceptor {

    /** Các route sửa bài: metadata, chuyển trạng thái, test case, và mọi route /admin/problems/{id}/** (trừ clone). */
    private static final Pattern EDIT_PROBLEM = Pattern.compile(
            "^/api/v1/problems/\\d+(/transitions|/test-cases(/.*)?)?$|^/api/v1/admin/problems/\\d+/.+$");
    private static final User NO_OWNER = new User();

    private final ProblemRepository problems;
    private final ContestRepository contests;

    public OwnershipInterceptor(ProblemRepository problems, ContestRepository contests) {
        this.problems = problems;
        this.contests = contests;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("GET".equals(request.getMethod()) || request.getRequestURI().endsWith("/clone")) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal user) || user.getRole() != Role.INSTRUCTOR) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars == null) return true;
        String uri = request.getRequestURI();
        Optional<User> owner;
        if (uri.startsWith("/api/v1/admin/contests/") && vars.containsKey("contestId")) {
            owner = contests.findById(Long.valueOf(vars.get("contestId"))).map(c -> c.getCreatedBy() == null ? NO_OWNER : c.getCreatedBy());
        } else if (EDIT_PROBLEM.matcher(uri).matches()) {
            String id = vars.getOrDefault("problemId", vars.get("id"));
            owner = problems.findById(Long.valueOf(id)).map(p -> p.getCreatedBy() == null ? NO_OWNER : p.getCreatedBy());
        } else {
            return true; // nộp bài, chạy thử... không phải thao tác sửa nội dung
        }
        // Không tìm thấy thì để controller trả 404 như cũ.
        if (owner.isPresent() && !user.getId().equals(owner.get().getId())) {
            throw new ForbiddenOperationException("Read only: you can only edit content you created");
        }
        return true;
    }
}
