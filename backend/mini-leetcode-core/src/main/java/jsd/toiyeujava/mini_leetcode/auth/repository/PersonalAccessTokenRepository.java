package jsd.toiyeujava.mini_leetcode.auth.repository;

import jsd.toiyeujava.mini_leetcode.auth.entity.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

    /** Nạp sẵn User để filter xác thực không phải truy vấn thêm. */
    @Query("select t from PersonalAccessToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PersonalAccessToken> findWithUserByTokenHash(@Param("hash") String hash);

    List<PersonalAccessToken> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select count(t) from PersonalAccessToken t "
            + "where t.user.id = :userId and t.revoked = false and t.expiresAt > :now")
    long countActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
