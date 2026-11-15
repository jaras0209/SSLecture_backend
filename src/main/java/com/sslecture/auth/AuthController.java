package com.sslecture.auth;

import com.sslecture.auth.dto.LoginRequest;
import com.sslecture.auth.dto.MeResponse;
import com.sslecture.auth.dto.RefreshRequest;
import com.sslecture.auth.dto.RegisterRequest;
import com.sslecture.auth.dto.RegisterResponse;
import com.sslecture.auth.dto.ResetPasswordRequest;
import com.sslecture.auth.dto.TokenResponse;
import com.sslecture.auth.dto.UpdateProfileRequest;
import com.sslecture.auth.jwt.AuthenticatedUser;
import com.sslecture.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 認證 API（backend_api_spec.md 第 2 節）。社群登入與忘記密碼另行實作。 */
@RestController
@RequestMapping("/auth")
class AuthController {

  private final AuthService authService;
  private final ProfileService profileService;

  AuthController(AuthService authService, ProfileService profileService) {
    this.authService = authService;
    this.profileService = profileService;
  }

  @PostMapping("/login")
  ApiResponse<TokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return ApiResponse.ok(authService.login(request, http.getRemoteAddr()));
  }

  @PostMapping("/register")
  ApiResponse<RegisterResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    return ApiResponse.ok(authService.register(request, http.getRemoteAddr()));
  }

  @PostMapping("/refresh")
  ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ApiResponse.ok(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser current) {
    authService.logout(current);
    return ApiResponse.ok();
  }

  @PostMapping("/reset-password")
  ApiResponse<Void> resetPassword(
      @AuthenticationPrincipal AuthenticatedUser current,
      @Valid @RequestBody ResetPasswordRequest request,
      HttpServletRequest http) {
    authService.resetPassword(current, request, http.getRemoteAddr());
    return ApiResponse.ok();
  }

  @GetMapping("/me")
  ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthenticatedUser current) {
    return ApiResponse.ok(profileService.me(current.userId()));
  }

  @PatchMapping("/me")
  ApiResponse<MeResponse> updateMe(
      @AuthenticationPrincipal AuthenticatedUser current,
      @RequestBody UpdateProfileRequest request) {
    return ApiResponse.ok(profileService.update(current.userId(), request));
  }
}
