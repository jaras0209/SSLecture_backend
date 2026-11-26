package com.sslecture.auth.invite;

import com.sslecture.church.Church;
import com.sslecture.user.Role;
import com.sslecture.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 邀請碼（database_schema.md 2.17）。管理員以 /admin/invite-codes 產生、作廢，註冊時使用。 */
@Entity
@Table(name = "invite_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode {

  @Id
  @Column(length = 20)
  private String code;

  @Column(nullable = false, length = 20)
  private Role role;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "church_id")
  private Church church;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "used_by")
  private User usedBy;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;

  @Column(nullable = false)
  private boolean revoked;

  /** 有效：未作廢、未使用、未過期（database_schema.md 5.9）。 */
  public boolean isValid(OffsetDateTime now) {
    return !revoked && usedBy == null && expiresAt.isAfter(now);
  }

  /** 管理員產生的新邀請碼。 */
  public static InviteCode issue(
      String code, Role role, Church church, User createdBy, OffsetDateTime expiresAt) {
    InviteCode invite = new InviteCode();
    invite.code = code;
    invite.role = role;
    invite.church = church;
    invite.createdBy = createdBy;
    invite.expiresAt = expiresAt;
    return invite;
  }

  public boolean isUsed() {
    return usedBy != null;
  }

  public void revoke() {
    this.revoked = true;
  }

  public void markUsed(User user, OffsetDateTime now) {
    this.usedBy = user;
    this.usedAt = now;
  }
}
