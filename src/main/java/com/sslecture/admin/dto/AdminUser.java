package com.sslecture.admin.dto;

import com.sslecture.user.Gender;
import com.sslecture.user.LoginMethod;
import com.sslecture.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 管理員看到的帳號（backend_api_spec.md 第 11 節 User，不含密碼雜湊）。 */
public record AdminUser(
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
    boolean isActive,
    List<String> childUsernames,
    Instant lastLoginAt,
    Instant createdAt) {}
