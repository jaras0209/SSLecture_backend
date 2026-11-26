package com.sslecture.admin.dto;

/** {@code DELETE /admin/users/:username} 的回應。 */
public record DeletedUser(String username, CleanupCounts cleanup) {}
