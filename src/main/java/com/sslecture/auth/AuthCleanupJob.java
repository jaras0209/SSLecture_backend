package com.sslecture.auth;

import com.sslecture.auth.attempt.AttemptLimiter;
import com.sslecture.auth.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每小時刪除超過 24 小時的嘗試紀錄與已過期的 Refresh Token。 */
@Component
class AuthCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);

  private final AttemptLimiter limiter;
  private final SessionService sessions;

  AuthCleanupJob(AttemptLimiter limiter, SessionService sessions) {
    this.limiter = limiter;
    this.sessions = sessions;
  }

  @Scheduled(cron = "0 7 * * * *")
  void cleanUp() {
    int attempts = limiter.deleteOld();
    int tokens = sessions.deleteExpired();
    log.debug("Auth cleanup: {} attempts, {} refresh tokens deleted", attempts, tokens);
  }
}
