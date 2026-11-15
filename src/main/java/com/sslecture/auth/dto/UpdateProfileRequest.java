package com.sslecture.auth.dto;

/** {@code PATCH /auth/me}。欄位均選填：{@code null} 表示不改，空字串表示清除。 */
public record UpdateProfileRequest(
    String displayName, String realName, String avatarUrl, String email) {}
