package com.sslecture.auth.dto;

import com.sslecture.user.Role;
import java.util.UUID;

/** {@code POST /auth/register} 的回應。註冊後不會自動登入，前端接著呼叫 {@code /auth/login}。 */
public record RegisterResponse(RegisteredUser user) {

  public record RegisteredUser(UUID id, String username, Role role, String church) {}
}
