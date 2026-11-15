package com.sslecture.auth.attempt;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AuthAttemptRepository extends JpaRepository<AuthAttempt, Long> {

  /** 某帳號在 {@code since} 之後的失敗時間，新的在前。 */
  @Query(
      """
      select a.attemptedAt from AuthAttempt a
      where a.action = :action and a.subject = :subject and a.succeeded = false
        and a.attemptedAt > :since
      order by a.attemptedAt desc
      """)
  List<OffsetDateTime> findFailuresBySubject(
      AuthAction action, String subject, OffsetDateTime since, Limit limit);

  /** 某來源 IP 在 {@code since} 之後的失敗時間，新的在前。 */
  @Query(
      """
      select a.attemptedAt from AuthAttempt a
      where a.action = :action and a.ip = :ip and a.succeeded = false and a.attemptedAt > :since
      order by a.attemptedAt desc
      """)
  List<OffsetDateTime> findFailuresByIp(
      AuthAction action, String ip, OffsetDateTime since, Limit limit);

  /** 某帳號最後一次成功的時間；成功之前的失敗不再計算。 */
  @Query(
      """
      select max(a.attemptedAt) from AuthAttempt a
      where a.action = :action and a.subject = :subject and a.succeeded = true
      """)
  OffsetDateTime findLastSuccess(AuthAction action, String subject);

  @Modifying
  @Query("delete from AuthAttempt a where a.attemptedAt < :before")
  int deleteOlderThan(OffsetDateTime before);
}
