package com.sslecture.auth.dto;

import com.sslecture.user.Gender;
import com.sslecture.user.LoginMethod;
import com.sslecture.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code GET /auth/me}、{@code PATCH /auth/me} 的回應。{@code childUsernames} 只用於 parent。 */
public record MeResponse(
    UUID id,
    String username,
    Role role,
    String church,
    String displayName,
    String realName,
    String email,
    Gender gender,
    String avatarUrl,
    LoginMethod loginMethod,
    boolean mustChangePassword,
    List<String> childUsernames,
    Instant lastLoginAt,
    Instant createdAt) {}
