package com.sslecture.admin;

import com.sslecture.admin.dto.CreateInviteRequest;
import com.sslecture.admin.dto.CreatedInvite;
import com.sslecture.admin.dto.InviteCodeView;
import com.sslecture.auth.invite.InviteCode;
import com.sslecture.auth.invite.InviteCodeRepository;
import com.sslecture.church.Church;
import com.sslecture.church.ChurchRepository;
import com.sslecture.common.api.BusinessException;
import com.sslecture.common.api.ErrorCode;
import com.sslecture.user.AccountRules;
import com.sslecture.user.Role;
import com.sslecture.user.User;
import com.sslecture.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理員的邀請碼（backend_api_spec.md 第 8 節）。呼叫端須已確認是管理員。 */
@Service
public class InviteCodeAdminService {

  /** 去掉容易看錯的 0/O、1/I，共 32 個字元（前端 src/utils/id.ts 的 CODE_ALPHABET） */
  static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  static final int RANDOM_LENGTH = 8;
  static final int DEFAULT_EXPIRY_DAYS = 7;
  static final int MAX_EXPIRY_DAYS = 30;

  private static final Map<Role, String> PREFIX =
      Map.of(
          Role.STUDENT, "STU",
          Role.TEACHER, "TCH",
          Role.PASTOR, "PST",
          Role.PARENT, "PAR",
          Role.ADMIN, "ADM");

  private static final SecureRandom RANDOM = new SecureRandom();

  private final InviteCodeRepository inviteCodes;
  private final ChurchRepository churches;
  private final UserRepository users;
  private final Clock clock;

  InviteCodeAdminService(
      InviteCodeRepository inviteCodes,
      ChurchRepository churches,
      UserRepository users,
      Clock clock) {
    this.inviteCodes = inviteCodes;
    this.churches = churches;
    this.users = users;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<InviteCodeView> list() {
    return inviteCodes.findAllNewestFirst().stream().map(InviteCodeAdminService::view).toList();
  }

  /** 產生邀請碼：SS-{角色前綴}-{8 個隨機字元}，以密碼學安全的亂數產生。 */
  @Transactional
  public CreatedInvite create(CreateInviteRequest request, UUID createdBy) {
    Role role = AdminUserService.parseRole(request.role());
    int days = request.expiryDays() == null ? DEFAULT_EXPIRY_DAYS : request.expiryDays();
    if (days < 1 || days > MAX_EXPIRY_DAYS) {
      throw AccountRules.invalid("expiryDays", "須在 1–" + MAX_EXPIRY_DAYS + " 天之間");
    }
    Church church = null;
    if (role != Role.ADMIN) {
      String name = request.church() == null ? "" : request.church().strip();
      if (name.isEmpty()) {
        throw new BusinessException(ErrorCode.CHURCH_REQUIRED);
      }
      church =
          churches
              .findByName(name)
              .filter(Church::isActive)
              .orElseThrow(() -> AccountRules.invalid("church", "找不到此教會"));
    }
    User admin = users.getReferenceById(createdBy);
    OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(Duration.ofDays(days));
    String code;
    do {
      code = "SS-" + PREFIX.get(role) + "-" + random(RANDOM_LENGTH);
    } while (inviteCodes.existsById(code));
    inviteCodes.save(InviteCode.issue(code, role, church, admin, expiresAt));
    return new CreatedInvite(code, expiresAt.toInstant());
  }

  /** 作廢尚未使用的邀請碼。比對不分大小寫、去掉前後空白。 */
  @Transactional
  public void revoke(String rawCode) {
    String code = rawCode == null ? "" : rawCode.strip().toUpperCase(Locale.ROOT);
    InviteCode invite =
        inviteCodes
            .findForUpdate(code)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));
    if (invite.isUsed()) {
      throw new BusinessException(ErrorCode.INVITE_CODE_ALREADY_USED);
    }
    invite.revoke();
  }

  private static String random(int length) {
    StringBuilder out = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return out.toString();
  }

  private static InviteCodeView view(InviteCode c) {
    return new InviteCodeView(
        c.getCode(),
        c.getRole(),
        c.getChurch() == null ? null : c.getChurch().getName(),
        c.getCreatedBy() == null ? null : c.getCreatedBy().getUsername(),
        instant(c.getCreatedAt()),
        instant(c.getExpiresAt()),
        c.getUsedBy() == null ? null : c.getUsedBy().getUsername(),
        instant(c.getUsedAt()),
        c.isRevoked());
  }

  private static Instant instant(OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }
}
