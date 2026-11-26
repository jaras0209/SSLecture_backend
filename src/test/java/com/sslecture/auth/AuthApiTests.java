package com.sslecture.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 認證 API 的整合測試（MySQL + 種子帳號，密碼都是 123456）。
 *
 * <p>測試共用同一個資料庫，所以會改變帳號的測試都先註冊自己的帳號，不動種子帳號。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTests {

  private static final String STRONG_PASSWORD = "Abcdef12";
  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearAttempts() {
    jdbc.update("DELETE FROM auth_attempts");
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  private ResultActions postJson(String path, String json) throws Exception {
    return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions login(String identifier, String password) throws Exception {
    return postJson(
        "/auth/login",
        """
        {"identifier": "%s", "password": "%s"}\
        """
            .formatted(identifier, password));
  }

  /** 登入並回傳 [accessToken, refreshToken]。 */
  private String[] tokens(String identifier, String password) throws Exception {
    String body =
        login(identifier, password)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new String[] {
      JsonPath.read(body, "$.data.accessToken"), JsonPath.read(body, "$.data.refreshToken")
    };
  }

  private static MockHttpServletRequestBuilder bearer(
      MockHttpServletRequestBuilder request, String accessToken) {
    return request.header("Authorization", "Bearer " + accessToken);
  }

  /** 註冊一個新學員並回傳帳號名稱。 */
  private String registerStudent() throws Exception {
    String username = "user" + SEQ.incrementAndGet();
    postJson(
            "/auth/register",
            """
            {"username": "%s", "password": "%s", "church": "愛與話語"}\
            """
                .formatted(username, STRONG_PASSWORD))
        .andExpect(status().isOk());
    return username;
  }

  private ResultActions resetPassword(String accessToken, String json) throws Exception {
    return mvc.perform(
        bearer(post("/auth/reset-password"), accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /** 需等待的秒數：測試執行時已過了幾秒，所以只檢查範圍。 */
  private static ResultMatcher retryAfterAbout(int seconds) {
    return result -> {
      int value = Integer.parseInt(result.getResponse().getHeader("Retry-After"));
      assertThat(value).isBetween(seconds - 60, seconds);
    };
  }

  // ─── tests ─────────────────────────────────────────────────────────────────

  @Nested
  class Login {

    @Test
    void returnsTokensAndUser() throws Exception {
      login("student", "123456")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.accessToken").isString())
          .andExpect(jsonPath("$.data.refreshToken").isString())
          .andExpect(jsonPath("$.data.expiresIn").value(3600))
          .andExpect(jsonPath("$.data.user.username").value("student"))
          .andExpect(jsonPath("$.data.user.role").value("student"))
          .andExpect(jsonPath("$.data.user.church").value("愛與話語"))
          .andExpect(jsonPath("$.data.user.mustChangePassword").value(false));
    }

    @Test
    void identifierIgnoresCaseAndSurroundingSpaces() throws Exception {
      login("  TEACHER ", "123456").andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownAccountLookTheSame() throws Exception {
      login("student", "wrong")
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
      login("nobody-here", "123456")
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void locksAccountAfterFiveFailures() throws Exception {
      String username = registerStudent();
      for (int i = 0; i < 5; i++) {
        login(username, "wrong").andExpect(status().isUnauthorized());
      }
      // 被鎖期間即使密碼正確也回 429
      login(username, STRONG_PASSWORD)
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"))
          .andExpect(retryAfterAbout(900));
      // 其他帳號不受影響
      login("student", "123456").andExpect(status().isOk());
    }

    @Test
    void successClearsEarlierFailures() throws Exception {
      String username = registerStudent();
      for (int i = 0; i < 4; i++) {
        login(username, "wrong").andExpect(status().isUnauthorized());
      }
      login(username, STRONG_PASSWORD).andExpect(status().isOk());
      for (int i = 0; i < 4; i++) {
        login(username, "wrong").andExpect(status().isUnauthorized());
      }
      login(username, STRONG_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void socialOnlyAccountMustUseSocialLogin() throws Exception {
      jdbc.update(
          "INSERT INTO users (username, role, login_method) VALUES ('social.only', 'student',"
              + " 'google')");
      login("social.only", "anything")
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("USE_SOCIAL_LOGIN"));
    }

    @Test
    void disabledAccountIsRejectedOnlyWithCorrectPassword() throws Exception {
      String username = registerStudent();
      jdbc.update("UPDATE users SET is_active = FALSE WHERE username = ?", username);
      login(username, "wrong").andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
      login(username, STRONG_PASSWORD)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void missingFieldsAreValidationErrors() throws Exception {
      postJson("/auth/login", "{}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
  }

  @Nested
  class AccessToken {

    @Test
    void meReturnsCurrentUser() throws Exception {
      String[] t = tokens("parent", "123456");
      mvc.perform(bearer(get("/auth/me"), t[0]))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.username").value("parent"))
          .andExpect(jsonPath("$.data.role").value("parent"))
          .andExpect(jsonPath("$.data.loginMethod").value("credentials"))
          .andExpect(jsonPath("$.data.childUsernames[0]").value("student"))
          .andExpect(jsonPath("$.data.lastLoginAt").isString());
    }

    @Test
    void missingOrInvalidTokenIsUnauthorized() throws Exception {
      mvc.perform(get("/auth/me"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
      mvc.perform(bearer(get("/auth/me"), "not-a-jwt"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void disabledAccountIsRejectedImmediately() throws Exception {
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);
      jdbc.update("UPDATE users SET is_active = FALSE WHERE username = ?", username);
      mvc.perform(bearer(get("/auth/me"), t[0]))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void roleChangeInvalidatesExistingTokens() throws Exception {
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);
      jdbc.update(
          "UPDATE users SET role = 'teacher', token_version = token_version + 1 WHERE username = ?",
          username);
      mvc.perform(bearer(get("/auth/me"), t[0]))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
      postJson("/auth/refresh", "{\"refreshToken\": \"%s\"}".formatted(t[1]))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }
  }

  @Nested
  class RefreshAndLogout {

    @Test
    void refreshIssuesNewAccessTokenAndKeepsRefreshToken() throws Exception {
      String[] t = tokens("student", "123456");
      String body =
          postJson("/auth/refresh", "{\"refreshToken\": \"%s\"}".formatted(t[1]))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data.refreshToken").value(t[1]))
              .andExpect(jsonPath("$.data.expiresIn").value(3600))
              .andExpect(jsonPath("$.data.user").doesNotExist())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String newAccess = JsonPath.read(body, "$.data.accessToken");
      mvc.perform(bearer(get("/auth/me"), newAccess)).andExpect(status().isOk());
    }

    @Test
    void unknownRefreshTokenIsExpired() throws Exception {
      postJson("/auth/refresh", "{\"refreshToken\": \"nope\"}")
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    void idleSessionExpires() throws Exception {
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);
      jdbc.update(
          "UPDATE refresh_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' MINUTE"
              + " WHERE user_id = (SELECT id FROM users WHERE username = ?)",
          username);
      postJson("/auth/refresh", "{\"refreshToken\": \"%s\"}".formatted(t[1]))
          .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    void rememberMeKeepsSessionForThirtyDays() throws Exception {
      String username = registerStudent();
      postJson(
              "/auth/login",
              """
              {"identifier": "%s", "password": "%s", "rememberMe": true}\
              """
                  .formatted(username, STRONG_PASSWORD))
          .andExpect(status().isOk());
      Integer days =
          jdbc.queryForObject(
              "SELECT TIMESTAMPDIFF(DAY, t.last_used_at, t.expires_at) FROM refresh_tokens t"
                  + " JOIN users u ON u.id = t.user_id WHERE u.username = ?",
              Integer.class,
              username);
      assertThat(days).isEqualTo(30);
    }

    @Test
    void logoutEndsOnlyThisSession() throws Exception {
      String[] first = tokens("teacher", "123456");
      String[] second = tokens("teacher", "123456");

      mvc.perform(bearer(post("/auth/logout"), first[0]))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true));

      mvc.perform(bearer(get("/auth/me"), first[0]))
          .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
      postJson("/auth/refresh", "{\"refreshToken\": \"%s\"}".formatted(first[1]))
          .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
      mvc.perform(bearer(get("/auth/me"), second[0])).andExpect(status().isOk());
    }
  }

  @Nested
  class Register {

    @Test
    void createsStudentWithoutInviteCode() throws Exception {
      postJson(
              "/auth/register",
              """
              {"username": "  New.User ", "email": " New@Example.com ",
               "password": "%s", "church": "愛與話語"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.user.username").value("new.user"))
          .andExpect(jsonPath("$.data.user.role").value("student"))
          .andExpect(jsonPath("$.data.user.church").value("愛與話語"));
      assertThat(
              jdbc.queryForObject(
                  "SELECT email FROM users WHERE username = 'new.user'", String.class))
          .isEqualTo("new@example.com");
      login("NEW@example.com", STRONG_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void rejectsWeakPassword() throws Exception {
      postJson(
              "/auth/register",
              """
              {"username": "weak1", "password": "abcdef", "church": "愛與話語"}\
              """)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
    }

    @Test
    void rejectsBadUsername() throws Exception {
      postJson(
              "/auth/register",
              """
              {"username": "有中文", "password": "%s", "church": "愛與話語"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.error.message").value("username：只能使用英文字母、數字與 . _ -"));
    }

    @Test
    void rejectsTakenUsernameAndEmail() throws Exception {
      postJson(
              "/auth/register",
              """
              {"username": "STUDENT", "password": "%s", "church": "愛與話語"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));

      jdbc.update("UPDATE users SET email = 'taken@example.com' WHERE username = 'pastor'");
      postJson(
              "/auth/register",
              """
              {"username": "fresh1", "email": "TAKEN@example.com", "password": "%s",
               "church": "愛與話語"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }

    @Test
    void requiresChurchWithoutInviteCode() throws Exception {
      postJson(
              "/auth/register",
              """
              {"username": "nochurch", "password": "%s"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("CHURCH_REQUIRED"));
    }

    @Test
    void inviteCodeDecidesRoleAndChurchAndCanBeUsedOnce() throws Exception {
      jdbc.update(
          "INSERT INTO invite_codes (code, role, church_id, expires_at) VALUES ('SS-TCH-TEST0001',"
              + " 'teacher', (SELECT id FROM churches WHERE name = '愛與話語'), CURRENT_TIMESTAMP +"
              + " INTERVAL '1' DAY)");
      postJson(
              "/auth/register",
              """
              {"username": "invited.teacher", "password": "%s", "inviteCode": "ss-tch-test0001"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.user.role").value("teacher"))
          .andExpect(jsonPath("$.data.user.church").value("愛與話語"));

      postJson(
              "/auth/register",
              """
              {"username": "second.teacher", "password": "%s", "inviteCode": "SS-TCH-TEST0001"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("INVALID_INVITE_CODE"));
    }

    @Test
    void locksInviteCodeGuessingPerIp() throws Exception {
      for (int i = 0; i < 10; i++) {
        postJson(
                "/auth/register",
                """
                {"username": "guess%d", "password": "%s", "inviteCode": "SS-XXX-%08d"}\
                """
                    .formatted(i, STRONG_PASSWORD, i))
            .andExpect(jsonPath("$.error.code").value("INVALID_INVITE_CODE"));
      }
      postJson(
              "/auth/register",
              """
              {"username": "guess99", "password": "%s", "inviteCode": "SS-XXX-99999999"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isTooManyRequests())
          .andExpect(retryAfterAbout(3600));
    }

    @Test
    void parentMustBindAStudentOfTheSameChurch() throws Exception {
      jdbc.update(
          "INSERT INTO invite_codes (code, role, church_id, expires_at) VALUES ('SS-PAR-TEST0001',"
              + " 'parent', (SELECT id FROM churches WHERE name = '愛與話語'), CURRENT_TIMESTAMP +"
              + " INTERVAL '1' DAY)");
      postJson(
              "/auth/register",
              """
              {"username": "new.parent", "password": "%s", "inviteCode": "SS-PAR-TEST0001",
               "childUsernames": ["teacher"]}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

      postJson(
              "/auth/register",
              """
              {"username": "new.parent", "password": "%s", "inviteCode": "SS-PAR-TEST0001",
               "childUsernames": ["Student", "teacher"]}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.user.role").value("parent"));

      String[] t = tokens("new.parent", STRONG_PASSWORD);
      mvc.perform(bearer(get("/auth/me"), t[0]))
          .andExpect(jsonPath("$.data.childUsernames.length()").value(1))
          .andExpect(jsonPath("$.data.childUsernames[0]").value("student"));
    }
  }

  @Nested
  class Password {

    @Test
    void adminResetForcesPasswordChangeAndEndsSessions() throws Exception {
      String username = registerStudent();
      String[] userTokens = tokens(username, STRONG_PASSWORD);
      String[] admin = tokens("admin", "123456");

      resetPassword(
              admin[0],
              """
              {"username": "%s", "newPassword": "temp"}\
              """
                  .formatted(username))
          .andExpect(status().isOk());

      // 既有的登入失效
      mvc.perform(bearer(get("/auth/me"), userTokens[0]))
          .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));

      // 用新密碼登入後，只能改密碼
      String[] t = tokens(username, "temp");
      login(username, "temp").andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
      mvc.perform(bearer(get("/courses"), t[0]))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
      mvc.perform(bearer(get("/auth/me"), t[0]))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.mustChangePassword").value(true));

      resetPassword(
              t[0],
              """
              {"oldPassword": "temp", "newPassword": "%s"}\
              """
                  .formatted("Brand-new-1"))
          .andExpect(status().isOk());
      mvc.perform(bearer(get("/auth/me"), t[0]))
          .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void onlyAdminCanResetOthers() throws Exception {
      String[] teacher = tokens("teacher", "123456");
      resetPassword(
              teacher[0],
              """
              {"username": "student", "newPassword": "whatever"}\
              """)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void selfChangeChecksOldPasswordAndStrength() throws Exception {
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);

      resetPassword(
              t[0],
              """
              {"oldPassword": "wrong", "newPassword": "Another-1"}\
              """)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("oldPassword：目前密碼不正確"));
      resetPassword(
              t[0],
              """
              {"oldPassword": "%s", "newPassword": "abcdef"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
      resetPassword(
              t[0],
              """
              {"oldPassword": "%s", "newPassword": "%s"}\
              """
                  .formatted(STRONG_PASSWORD, STRONG_PASSWORD))
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void selfChangeEndsOtherSessionsButKeepsThisOne() throws Exception {
      String username = registerStudent();
      String[] here = tokens(username, STRONG_PASSWORD);
      String[] elsewhere = tokens(username, STRONG_PASSWORD);

      resetPassword(
              here[0],
              """
              {"oldPassword": "%s", "newPassword": "Changed-pw-1"}\
              """
                  .formatted(STRONG_PASSWORD))
          .andExpect(status().isOk());

      mvc.perform(bearer(get("/auth/me"), here[0])).andExpect(status().isOk());
      mvc.perform(bearer(get("/auth/me"), elsewhere[0]))
          .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
      login(username, "Changed-pw-1").andExpect(status().isOk());
    }
  }

  @Nested
  class Profile {

    @Test
    void updatesAndClearsFields() throws Exception {
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);

      mvc.perform(
              bearer(patch("/auth/me"), t[0])
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"displayName": " 小明 ", "avatarUrl": "https://example.com/a.png",
                       "email": "Ming@Example.com"}\
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.displayName").value("小明"))
          .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/a.png"))
          .andExpect(jsonPath("$.data.email").value("ming@example.com"));

      mvc.perform(
              bearer(patch("/auth/me"), t[0])
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\": \"\", \"avatarUrl\": \"\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.displayName").doesNotExist())
          .andExpect(jsonPath("$.data.avatarUrl").doesNotExist())
          .andExpect(jsonPath("$.data.email").value("ming@example.com"));
    }

    @Test
    void rejectsInsecureAvatarWithoutSavingAnything() throws Exception {
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);

      mvc.perform(
              bearer(patch("/auth/me"), t[0])
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"displayName": "改了嗎", "avatarUrl": "http://tracker.example/a.png"}\
                      """))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("avatarUrl：只接受 https:// 開頭的網址"));

      mvc.perform(bearer(get("/auth/me"), t[0]))
          .andExpect(jsonPath("$.data.displayName").doesNotExist());
    }

    @Test
    void rejectsEmailOfAnotherAccount() throws Exception {
      jdbc.update("UPDATE users SET email = 'teacher@example.com' WHERE username = 'teacher'");
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);
      mvc.perform(
              bearer(patch("/auth/me"), t[0])
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\": \"TEACHER@example.com\"}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }
  }

  @Nested
  class FieldLimits {

    @Test
    void identifierLongerThanAnEmailIsRejected() throws Exception {
      login("a".repeat(255), "123456")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.error.message").value("identifier：最多 254 個字元"));
    }

    @Test
    void newPasswordLongerThan64IsWeak() throws Exception {
      postJson(
              "/auth/register",
              """
              {"username": "longpw", "password": "%s", "church": "愛與話語"}\
              """
                  .formatted("Aa1" + "x".repeat(62)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
    }

    @Test
    void adminResetPasswordLongerThan64IsRejected() throws Exception {
      String username = registerStudent();
      String[] admin = tokens("admin", "123456");
      resetPassword(
              admin[0],
              """
              {"username": "%s", "newPassword": "%s"}\
              """
                  .formatted(username, "x".repeat(65)))
          .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
    }

    @Test
    void inviteCodeLongerThan20IsRejected() throws Exception {
      postJson(
              "/auth/register",
              """
              {"username": "longcode", "password": "%s", "inviteCode": "%s"}\
              """
                  .formatted(STRONG_PASSWORD, "X".repeat(21)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("inviteCode：最多 20 個字元"));
    }

    @Test
    void profileNamesFollowTheirLimits() throws Exception {
      String username = registerStudent();
      String[] t = tokens(username, STRONG_PASSWORD);

      mvc.perform(
              bearer(patch("/auth/me"), t[0])
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"displayName": "%s", "realName": "%s"}\
                      """
                          .formatted("暱".repeat(20), "名".repeat(50))))
          .andExpect(status().isOk());
      mvc.perform(
              bearer(patch("/auth/me"), t[0])
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\": \"%s\"}".formatted("暱".repeat(21))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("displayName：最多 20 個字元"));
      mvc.perform(
              bearer(patch("/auth/me"), t[0])
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"realName\": \"%s\"}".formatted("名".repeat(51))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.message").value("realName：最多 50 個字元"));
    }
  }
}
