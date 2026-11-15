package com.sslecture.auth.session;

import com.sslecture.common.api.BusinessException;
import com.sslecture.common.api.ErrorCode;
import com.sslecture.config.AppProperties;
import com.sslecture.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理登入的工作階段（Refresh Token）。
 *
 * <p>Refresh Token 是隨機字串，資料庫只存 SHA-256 雜湊值。換發時不更換 Refresh Token，只順延閒置期限：前端多個分頁共用同一組
 * Token，若每次換發都換新的，同時換發的分頁會互相把對方登出。
 */
@Service
public class SessionService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final RefreshTokenRepository repository;
  private final AppProperties.Jwt jwt;
  private final Clock clock;

  SessionService(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
    this.repository = repository;
    this.jwt = properties.jwt();
    this.clock = clock;
  }

  /** 新的工作階段與交給前端的 Refresh Token（原文只在此時出現一次）。 */
  public record Started(RefreshToken session, String refreshToken) {}

  @Transactional
  public Started start(User user, boolean rememberMe) {
    String token = newToken();
    RefreshToken session =
        repository.save(
            new RefreshToken(user, hash(token), rememberMe, now(), idleTimeout(rememberMe)));
    return new Started(session, token);
  }

  /**
   * 使用 Refresh Token：順延閒置期限並回傳工作階段（含使用者）。
   *
   * <p>已撤銷、已過期、帳號已要求重新登入時一律回 {@code REFRESH_TOKEN_EXPIRED}，前端據此導向登入頁。
   */
  @Transactional
  public RefreshToken use(String refreshToken) {
    OffsetDateTime now = now();
    RefreshToken session =
        repository
            .findByTokenHash(hash(refreshToken))
            .filter(s -> s.isUsable(now))
            .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED));
    session.touch(now, idleTimeout(session.isRememberMe()));
    return session;
  }

  /** 驗證 Access Token 時讀取它所屬的工作階段。 */
  @Transactional(readOnly = true)
  public Optional<RefreshToken> find(UUID sessionId) {
    return repository.findWithUserById(sessionId);
  }

  /** 登出：只撤銷這一個工作階段。 */
  @Transactional
  public void revoke(UUID sessionId) {
    repository.findById(sessionId).ifPresent(RefreshToken::revoke);
  }

  /** 使用者自己改密碼：撤銷其他裝置的登入，保留目前這個。 */
  @Transactional
  public void revokeOthers(UUID userId, UUID keepSessionId) {
    repository.revokeOthers(userId, keepSessionId);
  }

  @Transactional
  public int deleteExpired() {
    return repository.deleteExpiredBefore(now());
  }

  private Duration idleTimeout(boolean rememberMe) {
    return rememberMe ? jwt.refreshTokenExpiryRemember() : jwt.refreshTokenExpiryDefault();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
