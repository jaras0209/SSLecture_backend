package com.sslecture.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /auth/reset-password}。
 *
 * @param username 管理員重設他人密碼時填寫；修改自己的密碼時省略
 * @param oldPassword 修改自己的密碼時必填
 */
public record ResetPasswordRequest(
    String username, String oldPassword, @NotBlank(message = "必填") String newPassword) {}
