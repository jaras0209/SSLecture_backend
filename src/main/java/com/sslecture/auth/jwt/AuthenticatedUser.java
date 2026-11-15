package com.sslecture.auth.jwt;

import com.sslecture.user.Role;
import java.util.UUID;

/**
 * 目前登入的使用者（Spring Security 的 principal）。在 Controller 以 {@code @AuthenticationPrincipal} 取得。
 *
 * <p>{@code role} 是這次請求從資料庫讀出的，不是 Token 裡記載的。
 */
public record AuthenticatedUser(UUID userId, UUID sessionId, String username, Role role) {}
