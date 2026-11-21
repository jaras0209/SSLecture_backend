package com.sslecture.auth.dto;

import com.sslecture.user.AccountRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/login}。{@code identifier} 為帳號或信箱。 */
public record LoginRequest(
    @NotBlank(message = "必填") @Size(max = AccountRules.EMAIL_MAX, message = "最多 {max} 個字元") String identifier,
    @NotBlank(message = "必填") @Size(max = AccountRules.LOGIN_PASSWORD_MAX, message = "最多 {max} 個字元") String password,
    Boolean rememberMe) {

  public boolean remember() {
    return Boolean.TRUE.equals(rememberMe);
  }
}
