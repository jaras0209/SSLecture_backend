package com.sslecture.user;

import com.sslecture.common.api.BusinessException;
import com.sslecture.common.api.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 帳號欄位的規則，與前端 {@code src/utils/account.ts}、{@code usePasswordStrength.ts}、{@code avatar.ts} 相同。
 *
 * <p>前端已先檢查一次；後端再檢查，因為 API 可以不經過前端直接呼叫。
 */
public final class AccountRules {

  public static final int USERNAME_MIN = 3;
  public static final int USERNAME_MAX = 30;

  /** 信箱；也是登入時 {@code identifier} 的上限（帳號或信箱）。 */
  public static final int EMAIL_MAX = 254;

  public static final int DISPLAY_NAME_MAX = 20;
  public static final int REAL_NAME_MAX = 50;
  public static final int AVATAR_URL_MAX = 2048;
  public static final int INVITE_CODE_MAX = 20;
  public static final int PASSWORD_MIN = 6;

  /** 設定新密碼的上限：bcrypt 只取前 72 個位元組，超過的部分會被忽略。 */
  public static final int PASSWORD_MAX = 64;

  /** 登入時密碼欄位的上限：比 {@link #PASSWORD_MAX} 寬，讓既有的較長密碼仍能輸入。 */
  public static final int LOGIN_PASSWORD_MAX = 128;

  private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]+$");
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern UPPER = Pattern.compile("[A-Z]");
  private static final Pattern LOWER = Pattern.compile("[a-z]");
  private static final Pattern DIGIT = Pattern.compile("[0-9]");
  private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

  private AccountRules() {}

  /** 去掉前後空白並轉小寫；{@code null} 視為空字串。 */
  public static String normalize(String value) {
    return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
  }

  /** {@code username} 須已正規化。 */
  public static void checkUsername(String username) {
    if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
      throw invalid("username", "長度須為 " + USERNAME_MIN + "–" + USERNAME_MAX + " 個字元");
    }
    if (!USERNAME.matcher(username).matches()) {
      throw invalid("username", "只能使用英文字母、數字與 . _ -");
    }
  }

  /** {@code email} 須已正規化；空字串表示沒有信箱，視為有效。 */
  public static void checkEmail(String email) {
    if (email.isEmpty()) {
      return;
    }
    checkLength("email", email, EMAIL_MAX);
    if (!EMAIL.matcher(email).matches()) {
      throw invalid("email", "信箱格式不正確");
    }
  }

  /** 空字串表示使用預設頭像。 */
  public static void checkAvatarUrl(String avatarUrl) {
    if (avatarUrl.isEmpty()) {
      return;
    }
    if (!avatarUrl.startsWith("https://")) {
      throw invalid("avatarUrl", "只接受 https:// 開頭的網址");
    }
    checkLength("avatarUrl", avatarUrl, AVATAR_URL_MAX);
  }

  /** 長度上限（backend_api_spec.md〈欄位長度上限〉）。以字元計算，與 PostgreSQL 的 {@code VARCHAR(n)} 相同，中文與英文都算 1 個。 */
  public static void checkLength(String field, String value, int max) {
    if (value != null && value.codePointCount(0, value.length()) > max) {
      throw invalid(field, "最多 " + max + " 個字元");
    }
  }

  /**
   * 使用者自己設定的密碼：6–64 個字元，且符合以下任兩項：8 個字元以上、大小寫字母混合、數字、符號。
   *
   * <p>管理員設定的密碼不受強度限制（使用者下次登入須先更換），但同樣不可超過 64 個字元，見 {@link #checkPasswordLength}。
   */
  public static boolean isStrongEnough(String password) {
    if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
      return false;
    }
    int met = 0;
    if (password.length() >= 8) {
      met++;
    }
    if (UPPER.matcher(password).find() && LOWER.matcher(password).find()) {
      met++;
    }
    if (DIGIT.matcher(password).find()) {
      met++;
    }
    if (SYMBOL.matcher(password).find()) {
      met++;
    }
    return met >= 2;
  }

  public static void checkPasswordStrength(String password) {
    if (!isStrongEnough(password)) {
      throw new BusinessException(ErrorCode.WEAK_PASSWORD);
    }
  }

  /** 管理員設定的密碼只檢查上限；超過時同樣回 {@code WEAK_PASSWORD}（backend_api_spec.md〈密碼規則〉）。 */
  public static void checkPasswordLength(String password) {
    if (password.length() > PASSWORD_MAX) {
      throw new BusinessException(ErrorCode.WEAK_PASSWORD);
    }
  }

  /** {@code VALIDATION_ERROR}，訊息指出是哪個欄位（前端直接顯示）。 */
  public static BusinessException invalid(String field, String message) {
    return new BusinessException(ErrorCode.VALIDATION_ERROR, field + "：" + message);
  }
}
