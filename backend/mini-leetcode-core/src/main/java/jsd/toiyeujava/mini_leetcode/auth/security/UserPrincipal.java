package jsd.toiyeujava.mini_leetcode.auth.security;

import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;
    private final Role role;
    private final jsd.toiyeujava.mini_leetcode.entity.UserStatus status;
    private final Collection<? extends GrantedAuthority> authorities;
    /** true khi request xác thực bằng Personal Access Token (plugin IDE) thay vì JWT của web. */
    private final boolean viaPat;

    public UserPrincipal(User user) {
        this(user, false);
    }

    public UserPrincipal(User user, boolean viaPat) {
        this.viaPat = viaPat;
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isViaPat() {
        return viaPat;
    }

    public jsd.toiyeujava.mini_leetcode.entity.UserStatus getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != jsd.toiyeujava.mini_leetcode.entity.UserStatus.BLOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == jsd.toiyeujava.mini_leetcode.entity.UserStatus.ACTIVE;
    }
}
