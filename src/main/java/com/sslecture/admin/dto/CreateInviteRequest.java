package com.sslecture.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /admin/invite-codes}：{@code expiryDays} 預設 7 天（1–30）。 */
public record CreateInviteRequest(
    @NotBlank(message = "必填") String role, String church, Integer expiryDays) {}
