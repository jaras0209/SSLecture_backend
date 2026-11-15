package com.sslecture.auth.session;

import com.sslecture.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 一次登入（工作階段），以 Refresh Token 的雜湊值識別（database_schema.md 2.13）。
 *
 * <p>Access Token 帶著這一列的 {@code id}（{@code sid}），所以撤銷這一列也會讓對應的 Access Token 立即失效。
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "remember_me", nullable = false, updatable = false)
  private boolean rememberMe;

  @Column(name = "token_version", nullable = false, updatable = false)
  private int tokenVersion;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "is_revoked", nullable = false)
  private boolean revoked;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  RefreshToken(
      User user, String tokenHash, boolean rememberMe, OffsetDateTime now, Duration idleTimeout) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.rememberMe = rememberMe;
    this.tokenVersion = user.getTokenVersion();
    touch(now, idleTimeout);
  }

  /** 使用一次：閒置期限從現在重新計算。 */
  void touch(OffsetDateTime now, Duration idleTimeout) {
    this.lastUsedAt = now;
    this.expiresAt = now.plus(idleTimeout);
  }

  void revoke() {
    this.revoked = true;
  }

  /** 仍可使用：未撤銷、未過期，且帳號在簽發後沒有被要求重新登入。 */
  public boolean isUsable(OffsetDateTime now) {
    return !revoked && expiresAt.isAfter(now) && tokenVersion == user.getTokenVersion();
  }
}
