package com.sslecture.auth.attempt;

import com.sslecture.common.api.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 嘗試次數限制（backend_api_spec.md「嘗試次數限制」）。
 *
 * <p>同時以「帳號」與「來源 IP」各算一份失敗次數；達到上限時回 429，並在 {@code Retry-After} 告知還要等多久。只計算失敗，帳號成功一次後先前的失敗不再計算（IP
 * 的計數不因成功而清除）。被鎖期間不再記錄嘗試，所以一直重試也不會延長鎖定時間。
 */
@Service
public class AttemptLimiter {

  private static final Logger log = LoggerFactory.getLogger(AttemptLimiter.class);

  /** 在 {@code window} 內失敗 {@code maxFailures} 次就鎖住，直到最早那次失敗超過 {@code window}。 */
  record Rule(int maxFailures, Duration window) {}

  private static final Duration MINUTES_15 = Duration.ofMinutes(15);

  private static final Map<AuthAction, Rule> SUBJECT_RULES =
      Map.of(
          AuthAction.LOGIN, new Rule(5, MINUTES_15),
          AuthAction.PASSWORD_CHANGE, new Rule(5, MINUTES_15));

  private static final Map<AuthAction, Rule> IP_RULES =
      Map.of(
          AuthAction.LOGIN, new Rule(20, MINUTES_15),
          AuthAction.INVITE_CODE, new Rule(10, Duration.ofHours(1)));

  private final AuthAttemptRepository repository;
  private final Clock clock;

  AttemptLimiter(AuthAttemptRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * 被鎖住時拋出 429。
   *
   * @param subject 小寫的帳號或信箱；不知道是誰時傳 {@code null}，只檢查 IP
   */
  @Transactional(readOnly = true)
  public void check(AuthAction action, String subject, String ip) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    Duration wait = Duration.ZERO;

    Rule subjectRule = SUBJECT_RULES.get(action);
    if (subjectRule != null && subject != null) {
      OffsetDateTime since = now.minus(subjectRule.window());
      OffsetDateTime lastSuccess = repository.findLastSuccess(action, subject);
      if (lastSuccess != null && lastSuccess.isAfter(since)) {
        since = lastSuccess;
      }
      List<OffsetDateTime> failures =
          repository.findFailuresBySubject(
              action, subject, since, Limit.of(subjectRule.maxFailures()));
      wait = max(wait, remainingLock(subjectRule, failures, now));
    }

    Rule ipRule = IP_RULES.get(action);
    if (ipRule != null) {
      List<OffsetDateTime> failures =
          repository.findFailuresByIp(
              action, ip, now.minus(ipRule.window()), Limit.of(ipRule.maxFailures()));
      wait = max(wait, remainingLock(ipRule, failures, now));
    }

    if (wait.isPositive()) {
      log.warn(
          "Locked out: action={} subject={} ip={} retryAfter={}s",
          action.code(),
          subject,
          ip,
          wait.toSeconds());
      throw BusinessException.tooManyRequests(wait);
    }
  }

  /** 另開交易寫入：呼叫端接著拋出例外、回滾自己的交易時，這筆失敗仍要留下。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(AuthAction action, String subject, String ip) {
    repository.save(new AuthAttempt(action, subject, ip, false, OffsetDateTime.now(clock)));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordSuccess(AuthAction action, String subject, String ip) {
    repository.save(new AuthAttempt(action, subject, ip, true, OffsetDateTime.now(clock)));
  }

  /** 只需保留最近 24 小時（database_schema.md 2.22）。 */
  @Transactional
  public int deleteOld() {
    return repository.deleteOlderThan(OffsetDateTime.now(clock).minusHours(24));
  }

  /** {@code failures} 為新的在前、最多 {@code maxFailures} 筆；未達上限時回傳 0。 */
  private static Duration remainingLock(
      Rule rule, List<OffsetDateTime> failures, OffsetDateTime now) {
    if (failures.size() < rule.maxFailures()) {
      return Duration.ZERO;
    }
    OffsetDateTime unlockAt = failures.get(rule.maxFailures() - 1).plus(rule.window());
    Duration remaining = Duration.between(now, unlockAt);
    // 無條件進位到秒，避免前端顯示「0 秒後再試」
    long seconds = remaining.toSeconds() + (remaining.toNanosPart() > 0 ? 1 : 0);
    return Duration.ofSeconds(Math.max(seconds, 1));
  }

  private static Duration max(Duration a, Duration b) {
    return a.compareTo(b) >= 0 ? a : b;
  }
}
