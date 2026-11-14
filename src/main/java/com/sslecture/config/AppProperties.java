package com.sslecture.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 對應 application.yml 的 {@code app.*} 設定。 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, OAuth oauth, Frontend frontend, Cors cors) {

  /**
   * JWT 設定。
   *
   * @param secret Base64 編碼的 HMAC 金鑰（至少 256 bit）
   * @param accessTokenExpiry Access Token 有效期
   * @param refreshTokenExpiryDefault 未勾「記住我」的 Refresh Token 閒置期限
   * @param refreshTokenExpiryRemember 勾選「記住我」的 Refresh Token 閒置期限
   */
  public record Jwt(
      String secret,
      Duration accessTokenExpiry,
      Duration refreshTokenExpiryDefault,
      Duration refreshTokenExpiryRemember) {}

  /**
   * 社群登入設定。
   *
   * @param exchangeCodeTtl 導回前端時附帶的一次性 code 有效期
   */
  public record OAuth(Duration exchangeCodeTtl) {}

  /**
   * 前端位置。
   *
   * @param url 前端 SPA 的網址
   * @param callbackPath 社群登入完成後導回的路徑
   */
  public record Frontend(String url, String callbackPath) {}

  /**
   * 跨網域設定。
   *
   * @param allowedOrigins 允許的前端來源，不可使用 {@code *}
   */
  public record Cors(List<String> allowedOrigins) {}
}
