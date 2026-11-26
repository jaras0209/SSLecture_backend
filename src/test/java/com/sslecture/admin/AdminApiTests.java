package com.sslecture.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 管理員 API（backend_api_spec.md 第 8 節）的整合測試：帳號與邀請碼。
 *
 * <p>會改變帳號的測試都先建立自己的帳號，不動種子帳號。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTests {

  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private String admin;

  @BeforeEach
  void signInAsAdmin() throws Exception {
    jdbc.update("DELETE FROM auth_attempts");
    admin = token("admin", "123456");
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  private String token(String username, String password) throws Exception {
    String body =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"identifier\": \"%s\", \"password\": \"%s\"}"
                            .formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.data.accessToken");
  }

  private ResultActions call(MockHttpServletRequestBuilder request, String token, String json)
      throws Exception {
    request.header("Authorization", "Bearer " + token);
    if (json != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(json);
    }
    return mvc.perform(request);
  }

  /** 以管理員建立帳號，回傳帳號名稱 */
  private String createUser(String role, String extra) throws Exception {
    String username = role + ".u" + SEQ.incrementAndGet();
    call(
            post("/admin/users"),
            admin,
            """
            {"username": "%s", "password": "Temp-123", "role": "%s", "church": "愛與話語" %s}
            """
                .formatted(username, role, extra))
        .andExpect(status().isOk());
    return username;
  }

  private String id(String username) {
    return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", String.class, username);
  }

  // ─── tests ─────────────────────────────────────────────────────────────────

  @Test
  void onlyAdminsMayUseIt() throws Exception {
    String teacher = token("teacher", "123456");
    call(get("/admin/users"), teacher, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    call(post("/admin/invite-codes"), teacher, "{\"role\": \"admin\"}")
        .andExpect(status().isForbidden());
    mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
  }

  @Nested
  class Users {

    @Test
    void listsActiveAccountsPageByPageWithFilters() throws Exception {
      call(get("/admin/users?role=student&church=愛與話語&pageSize=500"), admin, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.page").value(1))
          .andExpect(jsonPath("$.data.items[*].username", hasItem("student")))
          .andExpect(jsonPath("$.data.items[*].role", not(hasItem("teacher"))))
          .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
      call(get("/admin/users?role=parent&pageSize=500"), admin, null)
          .andExpect(
              jsonPath("$.data.items[?(@.username == 'parent')].childUsernames[0]")
                  .value("student"));
      call(get("/admin/users?pageSize=1"), admin, null)
          .andExpect(jsonPath("$.data.items.length()").value(1))
          .andExpect(jsonPath("$.data.pageSize").value(1));
      call(get("/admin/users?pageSize=501"), admin, null).andExpect(status().isBadRequest());
    }

    @Test
    void createsAnAccountThatMustChangeItsPassword() throws Exception {
      String username = createUser("teacher", ", \"email\": \"New.Teacher@Example.TW\"");
      assertThat(
              jdbc.queryForMap(
                  "SELECT role, email, must_change_password FROM users WHERE username = ?",
                  username))
          .containsEntry("role", "teacher")
          .containsEntry("email", "new.teacher@example.tw")
          .containsEntry("must_change_password", true);
      token(username, "Temp-123"); // can sign in
    }

    @Test
    void refusesTakenNamesAndUnknownRoles() throws Exception {
      call(
              post("/admin/users"),
              admin,
              "{\"username\": \"student\", \"password\": \"x12345\", \"role\": \"student\","
                  + " \"church\": \"愛與話語\"}")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));
      call(
              post("/admin/users"),
              admin,
              "{\"username\": \"someone\", \"password\": \"x12345\", \"role\": \"boss\","
                  + " \"church\": \"愛與話語\"}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
      call(
              post("/admin/users"),
              admin,
              "{\"username\": \"someone\", \"password\": \"x12345\", \"role\": \"student\"}")
          .andExpect(jsonPath("$.error.code").value("CHURCH_REQUIRED"));
    }

    @Test
    void resettingThePasswordEndsTheSignInsAndMustBeChanged() throws Exception {
      String username = createUser("student", "");
      String userToken = token(username, "Temp-123");
      call(put("/admin/users/" + username), admin, "{\"password\": \"Rand0mTemp\"}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
      call(get("/auth/me"), userToken, null).andExpect(status().isUnauthorized());
      token(username, "Rand0mTemp");
    }

    @Test
    void changingTheRoleCleansUpByTheOldRole() throws Exception {
      String teacher = createUser("teacher", "");
      String student = createUser("student", "");
      jdbc.update(
          "INSERT INTO student_assignments (student_id, teacher_id) VALUES (?, ?)",
          id(student),
          id(teacher));
      jdbc.update(
          "INSERT INTO booking_sessions (teacher_id, course_title, lecturer_name, proposed_at)"
              + " VALUES (?, '課', '王', NOW(3) + INTERVAL 1 DAY)",
          id(teacher));
      jdbc.update(
          "INSERT INTO lecturers (name, title, linked_user_id) VALUES ('教師講師', 'lecturer', ?)",
          id(teacher));

      // teacher → pastor: mentoring and bookings released, the lecturer link kept (pastors teach)
      call(put("/admin/users/" + teacher), admin, "{\"role\": \"pastor\"}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.user.role").value("pastor"))
          .andExpect(jsonPath("$.data.cleanup.mentorAssignments").value(1))
          .andExpect(jsonPath("$.data.cleanup.cancelledBookings").value(1))
          .andExpect(jsonPath("$.data.cleanup.lecturerLinks").value(0));
      assertThat(
              jdbc.queryForObject(
                  "SELECT cancel_reason FROM booking_sessions WHERE teacher_id = ?",
                  String.class,
                  id(teacher)))
          .isEqualTo(AccountCleanup.REASON_ROLE_CHANGED);

      // pastor → parent: the lecturer link goes
      call(put("/admin/users/" + teacher), admin, "{\"role\": \"parent\"}")
          .andExpect(jsonPath("$.data.cleanup.lecturerLinks").value(1));
    }

    @Test
    void genderOnlyForStudentsAndTeachers() throws Exception {
      String student = createUser("student", "");
      call(put("/admin/users/" + student), admin, "{\"gender\": \"female\"}")
          .andExpect(jsonPath("$.data.user.gender").value("female"));
      String parent = createUser("parent", "");
      call(put("/admin/users/" + parent), admin, "{\"gender\": \"male\"}")
          .andExpect(status().isBadRequest());
    }

    @Test
    void deletingKeepsTheNameAndEndsTheSignIns() throws Exception {
      String student = createUser("student", "");
      String userToken = token(student, "Temp-123");
      call(delete("/admin/users/" + student), admin, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.username").value(student));
      call(get("/auth/me"), userToken, null).andExpect(status().is4xxClientError());
      call(get("/admin/users?pageSize=500"), admin, null)
          .andExpect(jsonPath("$.data.items[*].username", not(hasItem(student))));
      // the name stays taken: its records are kept under it
      mvc.perform(
              post("/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"username\": \"%s\", \"password\": \"Abcdef12\", \"church\": \"愛與話語\"}"
                          .formatted(student)))
          .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));
      call(delete("/admin/users/" + student), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void anAdminCannotDeleteThemselves() throws Exception {
      call(delete("/admin/users/admin"), admin, null).andExpect(status().isBadRequest());
    }
  }

  @Nested
  class InviteCodes {

    @Test
    void generatesListsAndIsUsableForRegistration() throws Exception {
      String body =
          call(
                  post("/admin/invite-codes"),
                  admin,
                  "{\"role\": \"teacher\", \"church\": \"主大明\", \"expiryDays\": 3}")
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data.code", matchesPattern("SS-TCH-[A-HJ-NP-Z2-9]{8}")))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String code = JsonPath.read(body, "$.data.code");

      call(get("/admin/invite-codes"), admin, null)
          .andExpect(jsonPath("$.data[0].code").value(code))
          .andExpect(jsonPath("$.data[0].church").value("主大明"))
          .andExpect(jsonPath("$.data[0].createdBy").value("admin"))
          .andExpect(jsonPath("$.data[0].revoked").value(false));

      String username = "invited.u" + SEQ.incrementAndGet();
      mvc.perform(
              post("/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"username\": \"%s\", \"password\": \"Abcdef12\", \"inviteCode\": \"%s\"}"
                          .formatted(username, code.toLowerCase())))
          .andExpect(jsonPath("$.data.user.role").value("teacher"))
          .andExpect(jsonPath("$.data.user.church").value("主大明"));

      call(delete("/admin/invite-codes/" + code), admin, null)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("INVITE_CODE_ALREADY_USED"));
    }

    @Test
    void adminCodesNeedNoChurchOthersDo() throws Exception {
      call(post("/admin/invite-codes"), admin, "{\"role\": \"admin\"}")
          .andExpect(jsonPath("$.data.code", matchesPattern("SS-ADM-.{8}")));
      call(post("/admin/invite-codes"), admin, "{\"role\": \"student\"}")
          .andExpect(jsonPath("$.error.code").value("CHURCH_REQUIRED"));
      call(
              post("/admin/invite-codes"),
              admin,
              "{\"role\": \"student\", \"church\": \"愛與話語\", \"expiryDays\": 31}")
          .andExpect(status().isBadRequest());
    }

    @Test
    void revokedCodesCannotBeUsed() throws Exception {
      String body =
          call(post("/admin/invite-codes"), admin, "{\"role\": \"student\", \"church\": \"愛與話語\"}")
              .andReturn()
              .getResponse()
              .getContentAsString();
      String code = JsonPath.read(body, "$.data.code");
      call(delete("/admin/invite-codes/" + code), admin, null).andExpect(status().isOk());
      mvc.perform(
              post("/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"username\": \"late.u%d\", \"password\": \"Abcdef12\", \"inviteCode\": \"%s\"}"
                          .formatted(SEQ.incrementAndGet(), code)))
          .andExpect(jsonPath("$.error.code").value("INVALID_INVITE_CODE"));
      call(delete("/admin/invite-codes/SS-STU-NOPE2345"), admin, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("INVITE_CODE_NOT_FOUND"));
    }
  }
}
