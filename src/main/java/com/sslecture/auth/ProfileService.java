package com.sslecture.auth;

import com.sslecture.auth.dto.MeResponse;
import com.sslecture.auth.dto.UpdateProfileRequest;
import com.sslecture.common.api.BusinessException;
import com.sslecture.common.api.ErrorCode;
import com.sslecture.user.AccountRules;
import com.sslecture.user.ParentChildRepository;
import com.sslecture.user.Role;
import com.sslecture.user.User;
import com.sslecture.user.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 目前登入者的個人資料（{@code GET / PATCH /auth/me}）。 */
@Service
public class ProfileService {

  private final UserRepository users;
  private final ParentChildRepository parentChildren;

  ProfileService(UserRepository users, ParentChildRepository parentChildren) {
    this.users = users;
    this.parentChildren = parentChildren;
  }

  @Transactional(readOnly = true)
  public MeResponse me(UUID userId) {
    return toResponse(load(userId));
  }

  /** 全部欄位檢查通過才寫入，不會只存一部分。角色、教會、性別、帳號名稱不能在這裡改。 */
  @Transactional
  public MeResponse update(UUID userId, UpdateProfileRequest request) {
    User user = load(userId);

    String displayName = strip(request.displayName());
    String realName = strip(request.realName());
    String avatarUrl = strip(request.avatarUrl());
    String email = request.email() == null ? null : AccountRules.normalize(request.email());

    if (displayName != null) {
      AccountRules.checkLength("displayName", displayName, AccountRules.DISPLAY_NAME_MAX);
    }
    if (realName != null) {
      AccountRules.checkLength("realName", realName, AccountRules.REAL_NAME_MAX);
    }
    if (avatarUrl != null) {
      AccountRules.checkAvatarUrl(avatarUrl);
    }
    if (email != null) {
      AccountRules.checkEmail(email);
      if (!email.isEmpty() && users.existsByEmailAndIdNot(email, userId)) {
        throw new BusinessException(ErrorCode.EMAIL_TAKEN);
      }
    }

    if (displayName != null) {
      user.setDisplayName(emptyToNull(displayName));
    }
    if (realName != null) {
      user.setRealName(emptyToNull(realName));
    }
    if (avatarUrl != null) {
      user.setAvatarUrl(emptyToNull(avatarUrl));
    }
    if (email != null && !email.equals(nullToEmpty(user.getEmail()))) {
      user.setEmail(emptyToNull(email));
      user.setEmailVerified(false);
    }
    return toResponse(user);
  }

  private User load(UUID userId) {
    return users
        .findWithChurchById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
  }

  private MeResponse toResponse(User user) {
    return new MeResponse(
        user.getId(),
        user.getUsername(),
        user.getRole(),
        AuthService.churchName(user),
        user.getDisplayName(),
        user.getRealName(),
        user.getEmail(),
        user.getGender(),
        user.getAvatarUrl(),
        user.getLoginMethod(),
        user.isMustChangePassword(),
        user.getRole() == Role.PARENT ? parentChildren.findChildUsernames(user.getId()) : null,
        toInstant(user.getLastLoginAt()),
        toInstant(user.getCreatedAt()));
  }

  private static Instant toInstant(OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }

  private static String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
