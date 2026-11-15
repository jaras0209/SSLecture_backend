package com.sslecture.auth.dto;

/**
 * 登入與換發的回應。換發時沒有 {@code user}。
 *
 * <p>換發時 {@code refreshToken} 與送來的相同（不更換），前端照常儲存即可。
 */
public record TokenResponse(
    String accessToken, String refreshToken, long expiresIn, LoginUser user) {}
