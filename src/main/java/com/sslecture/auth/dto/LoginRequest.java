package com.sslecture.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/login}。{@code identifier} 為帳號或信箱。 */
public record LoginRequest(
    @NotBlank(message = "必填") String identifier,
    @NotBlank(message = "必填") String password,
    Boolean rememberMe) {

  public boolean remember() {
    return Boolean.TRUE.equals(rememberMe);
  }
}
