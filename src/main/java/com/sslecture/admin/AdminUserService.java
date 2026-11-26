package com.sslecture.admin;

import com.sslecture.admin.dto.AdminUser;
import com.sslecture.admin.dto.CleanupCounts;
import com.sslecture.admin.dto.CreateUserRequest;
import com.sslecture.admin.dto.DeletedUser;
import com.sslecture.admin.dto.Page;
import com.sslecture.admin.dto.UpdateUserRequest;
import com.sslecture.admin.dto.UserChange;
import com.sslecture.auth.session.SessionService;
import com.sslecture.church.Church;
import com.sslecture.church.ChurchRepository;
import com.sslecture.common.api.BusinessException;
import com.sslecture.common.api.ErrorCode;
import com.sslecture.common.jpa.CodedEnum;
import com.sslecture.user.AccountRules;
import com.sslecture.user.Gender;
import com.sslecture.user.ParentChildRepository;
import com.sslecture.user.Role;
import com.sslecture.user.User;
import com.sslecture.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理員的帳號管理（backend_api_spec.md 第 8 節）。呼叫端須已確認是管理員。 */
@Service
public class AdminUserService {

  static final int MAX_PAGE_SIZE = 500;

  private final UserRepository users;
  private final ChurchRepository churches;
  private final ParentChildRepository parentChildren;
  private final PasswordEncoder passwordEncoder;
  private final SessionService sessions;
  private final AccountCleanup cleanup;

  AdminUserService(
      UserRepository users,
      ChurchRepository churches,
      ParentChildRepository parentChildren,
      PasswordEncoder passwordEncoder,
      SessionService sessions,
      AccountCleanup cleanup) {
    this.users = users;
    this.churches = churches;
    this.parentChildren = parentChildren;
    this.passwordEncoder = passwordEncoder;
    this.sessions = sessions;
    this.cleanup = cleanup;
  }

  /** 啟用中的帳號；{@code role}、{@code church} 為選填的篩選。 */
  @Transactional(readOnly = true)
  public Page<AdminUser> list(String role, String church, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw AccountRules.invalid("pageSize", "page 須 ≥ 1，pageSize 須在 1–" + MAX_PAGE_SIZE + " 之間");
    }
    Role roleFilter = role == null || role.isBlank() ? null : parseRole(role);
    String churchFilter = church == null || church.isBlank() ? null : church.strip();
    var result = users.findActive(roleFilter, churchFilter, PageRequest.of(page - 1, pageSize));
    List<AdminUser> items = result.getContent().stream().map(this::view).toList();
    return new Page<>(items, result.getTotalElements(), page, pageSize);
  }

  /** 建立帳號：帳號與信箱規則同註冊；第一次登入須先換掉管理員設定的密碼。 */
  @Transactional
  public AdminUser create(CreateUserRequest request) {
    String username = AccountRules.normalize(request.username());
    String email = AccountRules.normalize(request.email());
    AccountRules.checkUsername(username);
    AccountRules.checkEmail(email);
    AccountRules.checkPasswordLength(request.password());
    Role role = parseRole(request.role());
    Church church = role == Role.ADMIN ? null : findChurch(request.church());
    if (users.existsByUsername(username)) {
      throw new BusinessException(ErrorCode.USERNAME_TAKEN);
    }
    if (!email.isEmpty() && users.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.EMAIL_TAKEN);
    }
    User user =
        User.withPassword(username, passwordEncoder.encode(request.password()), role, church);
    user.setEmail(email.isEmpty() ? null : email);
    String displayName = request.displayName() == null ? "" : request.displayName().strip();
    AccountRules.checkLength("displayName", displayName, AccountRules.DISPLAY_NAME_MAX);
    user.setDisplayName(displayName.isEmpty() ? null : displayName);
    user.setMustChangePassword(true);
    return view(users.save(user));
  }

  /** 更新帳號。改角色時先依原本的角色清理（見 {@link AccountCleanup}）；改角色或設定密碼後，該帳號既有的登入全部失效。 */
  @Transactional
  public UserChange update(String username, UpdateUserRequest request) {
    User user = load(username);
    CleanupCounts counts = CleanupCounts.NONE;
    boolean endSessions = false;

    if (request.displayName() != null) {
      String displayName = request.displayName().strip();
      AccountRules.checkLength("displayName", displayName, AccountRules.DISPLAY_NAME_MAX);
      user.setDisplayName(displayName.isEmpty() ? null : displayName);
    }
    if (request.role() != null) {
      Role role = parseRole(request.role());
      if (role != user.getRole()) {
        counts = cleanup.release(user, user.getRole(), role);
        user.setRole(role);
        if (role == Role.ADMIN) {
          user.setChurch(null);
        }
        if (role != Role.STUDENT && role != Role.TEACHER) {
          user.setGender(null);
        }
        endSessions = true;
      }
    }
    if (request.church() != null && user.getRole() != Role.ADMIN) {
      user.setChurch(findChurch(request.church()));
    }
    if (request.gender() != null) {
      if (request.gender().isBlank()) {
        user.setGender(null);
      } else if (user.getRole() != Role.STUDENT && user.getRole() != Role.TEACHER) {
        throw AccountRules.invalid("gender", "只有學員與教師需要設定性別");
      } else {
        user.setGender(parse(Gender.class, "gender", request.gender()));
      }
    }
    if (request.password() != null) {
      // 給社群帳號一組已知的密碼，等於讓知道密碼的人能以帳號密碼登入並占用該帳號
      if (!user.hasPassword()) {
        throw AccountRules.invalid("password", "此帳號使用 Google / LINE 登入，不能設定密碼");
      }
      AccountRules.checkPasswordLength(request.password());
      user.setPasswordHash(passwordEncoder.encode(request.password()));
      user.setMustChangePassword(true);
      endSessions = true;
    }
    if (endSessions) {
      user.invalidateAllSessions();
      sessions.revokeAll(user.getId());
    }
    return new UserChange(view(user), counts);
  }

  /**
   * 停用帳號（軟刪除）：依角色清理、結束所有登入。帳號名稱保留，之後同名註冊回 409 USERNAME_TAKEN。
   *
   * @param currentUsername 執行的管理員；不能刪除自己（會失去管理權限）
   */
  @Transactional
  public DeletedUser delete(String username, String currentUsername) {
    User user = load(username);
    if (user.getUsername().equals(currentUsername)) {
      throw AccountRules.invalid("username", "不能刪除自己的帳號");
    }
    CleanupCounts counts = cleanup.release(user, user.getRole(), null);
    user.setActive(false);
    user.invalidateAllSessions();
    sessions.revokeAll(user.getId());
    return new DeletedUser(user.getUsername(), counts);
  }

  private User load(String username) {
    return users
        .findByUsername(AccountRules.normalize(username))
        .filter(User::isActive)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  private Church findChurch(String name) {
    String churchName = name == null ? "" : name.strip();
    if (churchName.isEmpty()) {
      throw new BusinessException(ErrorCode.CHURCH_REQUIRED);
    }
    return churches
        .findByName(churchName)
        .filter(Church::isActive)
        .orElseThrow(() -> AccountRules.invalid("church", "找不到此教會"));
  }

  static Role parseRole(String code) {
    return parse(Role.class, "role", code);
  }

  private static <E extends Enum<E> & CodedEnum> E parse(Class<E> type, String field, String code) {
    try {
      return CodedEnum.fromCode(type, code == null ? "" : code.strip());
    } catch (IllegalArgumentException ex) {
      throw AccountRules.invalid(field, "不是有效的值：" + code);
    }
  }

  AdminUser view(User user) {
    List<String> children =
        user.getRole() == Role.PARENT ? parentChildren.findChildUsernames(user.getId()) : null;
    return new AdminUser(
        user.getId(),
        user.getUsername(),
        user.getRole(),
        user.getChurch() == null ? null : user.getChurch().getName(),
        user.getDisplayName(),
        user.getRealName(),
        user.getEmail(),
        user.getGender(),
        user.getAvatarUrl(),
        user.getLoginMethod(),
        user.isMustChangePassword(),
        user.isActive(),
        children,
        instant(user.getLastLoginAt()),
        instant(user.getCreatedAt()));
  }

  private static java.time.Instant instant(OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }
}
