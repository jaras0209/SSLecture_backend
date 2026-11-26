package com.sslecture.admin.dto;

import java.time.Instant;

/** {@code POST /admin/invite-codes} 的回應。 */
public record CreatedInvite(String code, Instant expiresAt) {}
