package com.sslecture.auth.attempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 一次嘗試（database_schema.md 2.22）。只保留最近 24 小時。 */
@Entity
@Table(name = "auth_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuthAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30)
  private AuthAction action;

  private String subject;

  @Column(nullable = false, length = 45)
  private String ip;

  @Column(nullable = false)
  private boolean succeeded;

  @Column(name = "attempted_at", nullable = false)
  private OffsetDateTime attemptedAt;

  AuthAttempt(
      AuthAction action, String subject, String ip, boolean succeeded, OffsetDateTime attemptedAt) {
    this.action = action;
    this.subject = subject;
    this.ip = ip;
    this.succeeded = succeeded;
    this.attemptedAt = attemptedAt;
  }
}
