package com.sslecture.admin;

import com.sslecture.admin.dto.AdminUser;
import com.sslecture.admin.dto.CreateInviteRequest;
import com.sslecture.admin.dto.CreateUserRequest;
import com.sslecture.admin.dto.CreatedInvite;
import com.sslecture.admin.dto.DeletedUser;
import com.sslecture.admin.dto.InviteCodeView;
import com.sslecture.admin.dto.Page;
import com.sslecture.admin.dto.UpdateUserRequest;
import com.sslecture.admin.dto.UserChange;
import com.sslecture.auth.jwt.AuthenticatedUser;
import com.sslecture.common.api.ApiResponse;
import com.sslecture.common.api.BusinessException;
import com.sslecture.common.api.ErrorCode;
import com.sslecture.user.Role;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理員 API（backend_api_spec.md 第 8 節）：帳號與邀請碼。只有 admin 可用，其他角色回 403 FORBIDDEN。 */
@RestController
@RequestMapping("/admin")
class AdminController {

  private final AdminUserService userService;
  private final InviteCodeAdminService inviteService;

  AdminController(AdminUserService userService, InviteCodeAdminService inviteService) {
    this.userService = userService;
    this.inviteService = inviteService;
  }

  @GetMapping("/users")
  ApiResponse<Page<AdminUser>> users(
      @AuthenticationPrincipal AuthenticatedUser current,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String church,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    requireAdmin(current);
    return ApiResponse.ok(userService.list(role, church, page, pageSize));
  }

  @PostMapping("/users")
  ApiResponse<AdminUser> createUser(
      @AuthenticationPrincipal AuthenticatedUser current,
      @Valid @RequestBody CreateUserRequest request) {
    requireAdmin(current);
    return ApiResponse.ok(userService.create(request));
  }

  @PutMapping("/users/{username}")
  ApiResponse<UserChange> updateUser(
      @AuthenticationPrincipal AuthenticatedUser current,
      @PathVariable String username,
      @RequestBody UpdateUserRequest request) {
    requireAdmin(current);
    return ApiResponse.ok(userService.update(username, request));
  }

  @DeleteMapping("/users/{username}")
  ApiResponse<DeletedUser> deleteUser(
      @AuthenticationPrincipal AuthenticatedUser current, @PathVariable String username) {
    requireAdmin(current);
    return ApiResponse.ok(userService.delete(username, current.username()));
  }

  @GetMapping("/invite-codes")
  ApiResponse<List<InviteCodeView>> inviteCodes(
      @AuthenticationPrincipal AuthenticatedUser current) {
    requireAdmin(current);
    return ApiResponse.ok(inviteService.list());
  }

  @PostMapping("/invite-codes")
  ApiResponse<CreatedInvite> createInviteCode(
      @AuthenticationPrincipal AuthenticatedUser current,
      @Valid @RequestBody CreateInviteRequest request) {
    requireAdmin(current);
    return ApiResponse.ok(inviteService.create(request, current.userId()));
  }

  @DeleteMapping("/invite-codes/{code}")
  ApiResponse<Void> revokeInviteCode(
      @AuthenticationPrincipal AuthenticatedUser current, @PathVariable String code) {
    requireAdmin(current);
    inviteService.revoke(code);
    return ApiResponse.ok();
  }

  private static void requireAdmin(AuthenticatedUser current) {
    if (current == null || current.role() != Role.ADMIN) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }
}
