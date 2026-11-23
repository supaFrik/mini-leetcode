package jsd.toiyeujava.mini_leetcode.auth.permission;

import jsd.toiyeujava.mini_leetcode.entity.Permission;
import jsd.toiyeujava.mini_leetcode.entity.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Quyền theo role (bảng {@code role_permissions}, V23, V24). ADMIN luôn đủ quyền, USER không có quyền nào;
 * chỉ INSTRUCTOR được bật/tắt, và không bao giờ có SYSTEM_MANAGE.
 */
@Service
public class RolePermissionService {

    /** Các quyền ADMIN được bật/tắt cho INSTRUCTOR. */
    public static final Set<Permission> INSTRUCTOR_EDITABLE = EnumSet.complementOf(EnumSet.of(Permission.SYSTEM_MANAGE));

    private final JdbcTemplate jdbc;

    public RolePermissionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Set<Permission> forRole(Role role) {
        Set<Permission> out = EnumSet.noneOf(Permission.class);
        if (role == Role.ADMIN) return EnumSet.allOf(Permission.class);
        if (role != Role.INSTRUCTOR) return out;
        jdbc.queryForList("SELECT permission FROM role_permissions WHERE role = ?", String.class, role.name())
                .forEach(p -> INSTRUCTOR_EDITABLE.stream().filter(e -> e.name().equals(p)).forEach(out::add));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<Role, Set<Permission>> all() {
        Map<Role, Set<Permission>> out = new EnumMap<>(Role.class);
        for (Role r : Role.values()) out.put(r, forRole(r));
        return out;
    }

    /** Chỉ nhận khoá INSTRUCTOR với quyền trong {@link #INSTRUCTOR_EDITABLE}; sai thì IllegalArgumentException (400). */
    @Transactional
    public Map<Role, Set<Permission>> update(Map<Role, Set<Permission>> changes) {
        if (changes == null) throw new IllegalArgumentException("Request body is required");
        changes.forEach((role, perms) -> {
            if (role != Role.INSTRUCTOR) throw new IllegalArgumentException("Only INSTRUCTOR permissions can be changed");
            if (perms == null || perms.contains(null) || !INSTRUCTOR_EDITABLE.containsAll(perms)) {
                throw new IllegalArgumentException("Instructors can only be granted " + INSTRUCTOR_EDITABLE);
            }
        });
        Set<Permission> perms = changes.get(Role.INSTRUCTOR);
        if (perms != null) {
            jdbc.update("DELETE FROM role_permissions WHERE role = ?", Role.INSTRUCTOR.name());
            perms.forEach(p -> jdbc.update("INSERT INTO role_permissions (role, permission) VALUES (?, ?)",
                    Role.INSTRUCTOR.name(), p.name()));
        }
        return all();
    }
}
