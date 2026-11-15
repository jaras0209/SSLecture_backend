package com.sslecture.auth.dto;

import com.sslecture.user.Role;
import java.util.UUID;

/** 登入回應裡的使用者摘要。 */
public record LoginUser(
    UUID id,
    String username,
    Role role,
    String church,
    String email,
    String avatarUrl,
    boolean mustChangePassword) {}
