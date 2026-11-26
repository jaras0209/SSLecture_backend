package com.sslecture.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 資料庫擋下的寫入轉成 API 錯誤（不再是 500）。以真正的 MySQL 產生錯誤，確認依它的訊息判斷得出是哪個限制。
 *
 * <p>每個測試在交易中執行並回滾。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataIntegrityErrorTests {

  private static final String CHURCH = "(SELECT id FROM churches WHERE name = '愛與話語')";

  @Autowired JdbcTemplate jdbc;

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void duplicateUsernameIsUsernameTaken() {
    assertError(
        "INSERT INTO users (username, role) VALUES ('student', 'student')",
        HttpStatus.CONFLICT,
        "USERNAME_TAKEN");
  }

  @Test
  void emailThatDiffersOnlyInCaseIsEmailTaken() {
    jdbc.update(
        "INSERT INTO users (username, role, email) VALUES ('mail.a', 'student', 'a@example.tw')");
    assertError(
        "INSERT INTO users (username, role, email) VALUES ('mail.b', 'student', 'A@Example.TW')",
        HttpStatus.CONFLICT,
        "EMAIL_TAKEN");
  }

  @Test
  void sameLecturerInTheSameChurchIsLecturerExists() {
    jdbc.update(
        "INSERT INTO lecturers (name, title, church_id) VALUES ('王大明', 'pastor', " + CHURCH + ")");
    assertError(
        "INSERT INTO lecturers (name, title, church_id) VALUES ('王大明', 'pastor', " + CHURCH + ")",
        HttpStatus.CONFLICT,
        "LECTURER_EXISTS");
  }

  @Test
  void accountLinkedTwiceIsLecturerAccountLinked() {
    String teacher = "(SELECT id FROM users WHERE username = 'teacher')";
    jdbc.update(
        "INSERT INTO lecturers (name, title, church_id, linked_user_id) VALUES ('甲', 'pastor', "
            + CHURCH
            + ", "
            + teacher
            + ")");
    assertError(
        "INSERT INTO lecturers (name, title, church_id, linked_user_id) VALUES ('乙', 'pastor', "
            + CHURCH
            + ", "
            + teacher
            + ")",
        HttpStatus.CONFLICT,
        "LECTURER_ACCOUNT_LINKED");
  }

  @Test
  void duplicateThemeIsThemeExists() {
    assertError(
        "INSERT INTO themes (church_id, type, name) VALUES ("
            + CHURCH
            + ", 'character', '品格力 - 自律')",
        HttpStatus.CONFLICT,
        "THEME_EXISTS");
  }

  @Test
  void otherRulesAreValidationErrorsNamingTheRule() {
    ResponseEntity<ApiResponse<Void>> response =
        assertError(
            "INSERT INTO users (username, role) VALUES ('Bad Name', 'student')",
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR");
    assertThat(response.getBody().error().message()).contains("users_username_format");
  }

  @Test
  void otherDuplicatesAreValidationErrors() {
    jdbc.update(
        "INSERT INTO page_restrictions (user_id, page_path)"
            + " VALUES ((SELECT id FROM users WHERE username = 'student'), '/shining')");
    assertError(
        "INSERT INTO page_restrictions (user_id, page_path)"
            + " VALUES ((SELECT id FROM users WHERE username = 'student'), '/shining')",
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR");
  }

  @Test
  void textTooLongIsValidationError() {
    assertError(
        "UPDATE users SET display_name = '" + "字".repeat(21) + "' WHERE username = 'student'",
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR");
  }

  private ResponseEntity<ApiResponse<Void>> assertError(
      String sql, HttpStatus status, String code) {
    DataIntegrityViolationException ex =
        catchThrowableOfType(DataIntegrityViolationException.class, () -> jdbc.update(sql));
    assertThat(ex).as("the database refuses: %s", sql).isNotNull();
    ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(ex);
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody().error().code()).isEqualTo(code);
    return response;
  }
}
