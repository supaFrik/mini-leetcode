package jsd.toiyeujava.mini_leetcode.auth.repository;

import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /** Tra cứu hàng loạt (thêm thí sinh theo danh sách) trong một truy vấn. */
    java.util.List<User> findByUsernameIn(java.util.Collection<String> usernames);

    java.util.List<User> findByEmailIn(java.util.Collection<String> emails);
}
