package com.sslecture.auth.session;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  @EntityGraph(attributePaths = "user")
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** 每個請求驗證 Access Token 時使用：一次讀出工作階段與使用者。 */
  @EntityGraph(attributePaths = "user")
  Optional<RefreshToken> findWithUserById(UUID id);

  @Modifying
  @Query(
      """
      update RefreshToken t set t.revoked = true
      where t.user.id = :userId and t.id <> :keepId and t.revoked = false
      """)
  int revokeOthers(UUID userId, UUID keepId);

  @Modifying
  @Query(
      "update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
  int revokeAll(UUID userId);

  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :before")
  int deleteExpiredBefore(OffsetDateTime before);
}
