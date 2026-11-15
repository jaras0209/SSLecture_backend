package com.sslecture.user;

import com.sslecture.church.Church;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 使用者（database_schema.md 2.1）。
 *
 * <p>{@code username}、{@code email} 一律小寫儲存；{@code passwordHash} 為 {@code null} 表示只能用社群登入。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Setter(AccessLevel.NONE)
  @Column(nullable = false, unique = true, length = 30, updatable = false)
  private String username;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(nullable = false, length = 20)
  private Role role;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "church_id")
  private Church church;

  @Column(name = "display_name", length = 100)
  private String displayName;

  @Column(name = "real_name", length = 100)
  private String realName;

  @Column(length = 10)
  private Gender gender;

  @Column(unique = true)
  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  @Column(name = "avatar_url", length = 2048)
  private String avatarUrl;

  @Setter(AccessLevel.NONE)
  @Column(name = "login_method", nullable = false, length = 20, updatable = false)
  private LoginMethod loginMethod;

  @Setter(AccessLevel.NONE)
  @Column(name = "token_version", nullable = false)
  private int tokenVersion;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "last_login_at")
  private OffsetDateTime lastLoginAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** 以帳號密碼註冊的新帳號；{@code username}、{@code email} 須已正規化（去空白、小寫）。 */
  public static User withPassword(String username, String passwordHash, Role role, Church church) {
    User user = new User();
    user.username = username;
    user.passwordHash = passwordHash;
    user.role = role;
    user.church = church;
    user.loginMethod = LoginMethod.CREDENTIALS;
    return user;
  }

  public boolean hasPassword() {
    return passwordHash != null;
  }

  /** 讓這個帳號既有的 Access Token 與 Refresh Token 全部失效（database_schema.md 5.15）。 */
  public void invalidateAllSessions() {
    tokenVersion++;
  }
}
