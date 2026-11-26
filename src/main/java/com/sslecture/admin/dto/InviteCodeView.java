package com.sslecture.admin.dto;

import com.sslecture.user.Role;
import java.time.Instant;

/** {@code GET /admin/invite-codes} 的一筆。 */
public record InviteCodeView(
    String code,
    Role role,
    String church,
    String createdBy,
    Instant createdAt,
    Instant expiresAt,
    String usedBy,
    Instant usedAt,
    boolean revoked) {}
