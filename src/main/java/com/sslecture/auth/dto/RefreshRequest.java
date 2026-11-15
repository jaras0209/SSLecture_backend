package com.sslecture.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /auth/refresh}。 */
public record RefreshRequest(@NotBlank(message = "必填") String refreshToken) {}
