package com.sslecture.auth.jwt;

import com.sslecture.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * 簽發與驗證 Access Token（HS256）。
 *
 * <p>Token 內容：{@code sub} 使用者 ID、{@code sid} 工作階段（Refresh Token）ID、{@code ver} 簽發時的 {@code
 * users.token_version}。角色不放在 Token 裡，每次請求都以資料庫為準（database_schema.md 5.15）。
 */
@Service
public class JwtService {

  private static final String SESSION_CLAIM = "sid";
  private static final String VERSION_CLAIM = "ver";

  private final SecretKey key;
  private final Duration expiry;
  private final Clock clock;

  JwtService(AppProperties properties, Clock clock) {
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.jwt().secret()));
    this.expiry = properties.jwt().accessTokenExpiry();
    this.clock = clock;
  }

  /** Access Token 裡記載的內容。 */
  public record AccessClaims(UUID userId, UUID sessionId, int tokenVersion) {}

  public String issue(UUID userId, UUID sessionId, int tokenVersion) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(userId.toString())
        .claim(SESSION_CLAIM, sessionId.toString())
        .claim(VERSION_CLAIM, tokenVersion)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiry)))
        .signWith(key)
        .compact();
  }

  /** 有效期限（秒），回應的 {@code expiresIn}。 */
  public long expiresInSeconds() {
    return expiry.toSeconds();
  }

  /**
   * 驗證簽章與期限。
   *
   * @throws io.jsonwebtoken.ExpiredJwtException 已過期
   * @throws JwtException 簽章錯誤或格式不符
   */
  public AccessClaims parse(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(key)
            .clock(() -> Date.from(clock.instant()))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    try {
      return new AccessClaims(
          UUID.fromString(claims.getSubject()),
          UUID.fromString(claims.get(SESSION_CLAIM, String.class)),
          claims.get(VERSION_CLAIM, Integer.class));
    } catch (RuntimeException e) {
      throw new JwtException("Malformed access token claims", e);
    }
  }
}
