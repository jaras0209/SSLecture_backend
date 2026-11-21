package com.sslecture.auth;

import com.sslecture.auth.attempt.AttemptLimiter;
import com.sslecture.auth.attempt.AuthAction;
import com.sslecture.auth.dto.LoginRequest;
import com.sslecture.auth.dto.LoginUser;
import com.sslecture.auth.dto.RegisterRequest;
import com.sslecture.auth.dto.RegisterResponse;
import com.sslecture.auth.dto.ResetPasswordRequest;
import com.sslecture.auth.dto.TokenResponse;
import com.sslecture.auth.invite.InviteCode;
import com.sslecture.auth.invite.InviteCodeRepository;
import com.sslecture.auth.jwt.AuthenticatedUser;
import com.sslecture.auth.jwt.JwtService;
import com.sslecture.auth.session.RefreshToken;
import com.sslecture.auth.session.SessionService;
import com.sslecture.church.Church;
import com.sslecture.church.ChurchRepository;
import com.sslecture.common.api.BusinessException;
import com.sslecture.common.api.ErrorCode;
import com.sslecture.user.AccountRules;
import com.sslecture.user.ParentChild;
import com.sslecture.user.ParentChildRepository;
import com.sslecture.user.Role;
import com.sslecture.user.User;
import com.sslecture.user.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 帳號密碼登入、註冊、換發、登出與修改密碼（backend_api_spec.md 第 2 節）。 */
@Service
public class AuthService {

  private final UserRepository users;
  private final ChurchRepository churches;
  private final InviteCodeRepository inviteCodes;
  private final ParentChildRepository parentChildren;
  private final SessionService sessions;
  private final JwtService jwt;
  private final AttemptLimiter limiter;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  /** 帳號不存在時也比對一次密碼，讓回應時間不透露帳號是否存在。 */
  private final String dummyHash;

  AuthService(
      UserRepository users,
      ChurchRepository churches,
      InviteCodeRepository inviteCodes,
      ParentChildRepository parentChildren,
      SessionService sessions,
      JwtService jwt,
      AttemptLimiter limiter,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.users = users;
    this.churches = churches;
    this.inviteCodes = inviteCodes;
    this.parentChildren = parentChildren;
    this.sessions = sessions;
    this.jwt = jwt;
    this.limiter = limiter;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
  }

  /**
   * 登入。帳號不存在與密碼錯誤都回 {@code INVALID_CREDENTIALS}；停用的帳號要密碼正確才回 {@code ACCOUNT_DISABLED}，否則就透露了帳號存在。
   */
  @Transactional
  public TokenResponse login(LoginRequest request, String ip) {
    String identifier = AccountRules.normalize(request.identifier());
    limiter.check(AuthAction.LOGIN, identifier, ip);

    User user = users.findByIdentifier(identifier).orElse(null);
    if (user == null) {
      passwordEncoder.matches(request.password(), dummyHash);
      limiter.recordFailure(AuthAction.LOGIN, identifier, ip);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (!user.hasPassword()) {
      limiter.recordFailure(AuthAction.LOGIN, identifier, ip);
      throw new BusinessException(ErrorCode.USE_SOCIAL_LOGIN);
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      limiter.recordFailure(AuthAction.LOGIN, identifier, ip);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
    }

    limiter.recordSuccess(AuthAction.LOGIN, identifier, ip);
    user.setLastLoginAt(now());
    SessionService.Started started = sessions.start(user, request.remember());
    return new TokenResponse(
        jwt.issue(user.getId(), started.session().getId(), user.getTokenVersion()),
        started.refreshToken(),
        jwt.expiresInSeconds(),
        toLoginUser(user));
  }

  /**
   * 註冊。有邀請碼時依邀請碼的角色與教會；沒有時一律為學員，且須選擇教會。
   *
   * <p>註冊後不自動登入（規格的回應沒有 Token）。
   */
  @Transactional
  public RegisterResponse register(RegisterRequest request, String ip) {
    String username = AccountRules.normalize(request.username());
    String email = AccountRules.normalize(request.email());
    AccountRules.checkUsername(username);
    AccountRules.checkEmail(email);
    AccountRules.checkPasswordStrength(request.password());

    Role role = Role.STUDENT;
    Church church = null;
    InviteCode invite = null;
    String code =
        request.inviteCode() == null ? "" : request.inviteCode().strip().toUpperCase(Locale.ROOT);
    AccountRules.checkLength("inviteCode", code, AccountRules.INVITE_CODE_MAX);
    if (!code.isEmpty()) {
      limiter.check(AuthAction.INVITE_CODE, null, ip);
      OffsetDateTime now = now();
      invite = inviteCodes.findForUpdate(code).filter(c -> c.isValid(now)).orElse(null);
      if (invite == null) {
        limiter.recordFailure(AuthAction.INVITE_CODE, null, ip);
        throw new BusinessException(ErrorCode.INVALID_INVITE_CODE);
      }
      role = invite.getRole();
      church = invite.getChurch();
    }

    if (role == Role.ADMIN) {
      church = null;
    } else if (church == null) {
      church = findChurch(request.church());
    }

    if (users.existsByUsername(username)) {
      throw new BusinessException(ErrorCode.USERNAME_TAKEN);
    }
    if (!email.isEmpty() && users.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.EMAIL_TAKEN);
    }

    List<User> children = List.of();
    if (role == Role.PARENT) {
      children = findChildren(request.childUsernames(), church);
    }

    User user =
        User.withPassword(username, passwordEncoder.encode(request.password()), role, church);
    user.setEmail(email.isEmpty() ? null : email);
    users.save(user);

    if (invite != null) {
      invite.markUsed(user, now());
    }
    for (User child : children) {
      parentChildren.save(new ParentChild(user.getId(), child.getId()));
    }

    return new RegisterResponse(
        new RegisterResponse.RegisteredUser(
            user.getId(), user.getUsername(), user.getRole(), churchName(user)));
  }

  /** 換發 Access Token，並順延閒置期限。帳號狀態與角色重新從資料庫讀取。 */
  @Transactional
  public TokenResponse refresh(String refreshToken) {
    RefreshToken session = sessions.use(refreshToken);
    User user = session.getUser();
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
    }
    return new TokenResponse(
        jwt.issue(user.getId(), session.getId(), user.getTokenVersion()),
        refreshToken,
        jwt.expiresInSeconds(),
        null);
  }

  @Transactional
  public void logout(AuthenticatedUser current) {
    sessions.revoke(current.sessionId());
  }

  /**
   * 修改密碼。
   *
   * <ul>
   *   <li>管理員重設他人密碼：不檢查強度，對方下次登入須先更換，且既有的登入全部失效
   *   <li>修改自己的密碼：須提供目前密碼、符合密碼規則；其他裝置的登入失效，保留目前這個
   * </ul>
   *
   * <p>目前密碼錯誤回 400 而不是 401：前端收到 401 會當成 Token 失效去換發。
   */
  @Transactional
  public void resetPassword(AuthenticatedUser current, ResetPasswordRequest request, String ip) {
    String target = AccountRules.normalize(request.username());
    if (!target.isEmpty() && !target.equals(current.username())) {
      resetOthersPassword(current, target, request.newPassword());
      return;
    }

    User user =
        users
            .findById(current.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    if (!user.hasPassword()) {
      throw AccountRules.invalid("oldPassword", "此帳號使用 Google / LINE 登入，沒有密碼");
    }
    if (request.oldPassword() == null || request.oldPassword().isEmpty()) {
      throw AccountRules.invalid("oldPassword", "必填");
    }
    limiter.check(AuthAction.PASSWORD_CHANGE, user.getUsername(), ip);
    if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
      limiter.recordFailure(AuthAction.PASSWORD_CHANGE, user.getUsername(), ip);
      throw AccountRules.invalid("oldPassword", "目前密碼不正確");
    }
    limiter.recordSuccess(AuthAction.PASSWORD_CHANGE, user.getUsername(), ip);
    if (Objects.equals(request.oldPassword(), request.newPassword())) {
      throw AccountRules.invalid("newPassword", "不可與目前密碼相同");
    }
    AccountRules.checkPasswordStrength(request.newPassword());

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    sessions.revokeOthers(user.getId(), current.sessionId());
  }

  private void resetOthersPassword(AuthenticatedUser current, String target, String newPassword) {
    if (current.role() != Role.ADMIN) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    User user =
        users
            .findByUsername(target)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    // 給社群帳號一組已知的密碼，等於讓知道密碼的人能以帳號密碼登入並占用該帳號
    if (!user.hasPassword()) {
      throw AccountRules.invalid("username", "此帳號使用 Google / LINE 登入，不能重設密碼");
    }
    AccountRules.checkPasswordLength(newPassword);
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setMustChangePassword(true);
    user.invalidateAllSessions();
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

  /** 家長綁定的孩子：只取同教會、啟用中的學員，其餘忽略；至少要有一位。 */
  private List<User> findChildren(List<String> usernames, Church church) {
    List<String> normalized =
        usernames == null
            ? List.of()
            : usernames.stream().map(AccountRules::normalize).distinct().toList();
    List<User> children =
        users.findByUsernameIn(normalized).stream()
            .filter(u -> u.getRole() == Role.STUDENT && u.isActive())
            .filter(u -> church != null && u.getChurch() != null)
            .filter(u -> u.getChurch().getId().equals(church.getId()))
            .toList();
    if (children.isEmpty()) {
      throw AccountRules.invalid("childUsernames", "家長須至少綁定一位同教會的學員");
    }
    return children;
  }

  private LoginUser toLoginUser(User user) {
    return new LoginUser(
        user.getId(),
        user.getUsername(),
        user.getRole(),
        churchName(user),
        user.getEmail(),
        user.getAvatarUrl(),
        user.isMustChangePassword());
  }

  static String churchName(User user) {
    return user.getChurch() == null ? null : user.getChurch().getName();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }
}
