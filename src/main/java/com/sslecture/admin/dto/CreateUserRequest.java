package com.sslecture.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /admin/users}：管理員建立的帳號，第一次登入須先換掉密碼。 */
public record CreateUserRequest(
    @NotBlank(message = "必填") String username,
    String email,
    @NotBlank(message = "必填") String password,
    @NotNull(message = "必填") String role,
    String church,
    String displayName) {}
