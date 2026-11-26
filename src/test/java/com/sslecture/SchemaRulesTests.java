package com.sslecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 資料庫守住的規則（changeset 001；database_schema.md 5.16、5.17）。
 *
 * <p>每個測試在交易中執行並回滾，不留下資料。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaRulesTests {

  private static final String STUDENT = "(SELECT id FROM users WHERE username = 'student')";
  private static final String TEACHER = "(SELECT id FROM users WHERE username = 'teacher')";

  @Autowired JdbcTemplate jdbc;

  @Test
  void displayNameIsLimitedTo20Characters() {
    assertThatCode(() ->
            jdbc.update(
                "UPDATE users SET display_name = ? WHERE username = 'student'", "一".repeat(20)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE users SET display_name = ? WHERE username = 'student'", "一".repeat(21)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void listenNotesAreLimitedTo2000Characters() {
    assertThatThrownBy(() -> insertListenSession("CURRENT_TIMESTAMP", "字".repeat(2001)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void listenedAtCannotBeMoreThanAnHourAhead() {
    assertThatCode(() -> insertListenSession("CURRENT_TIMESTAMP + INTERVAL '30' MINUTE", "剛結束的課"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> insertListenSession("CURRENT_TIMESTAMP + INTERVAL '2' HOUR", "未來"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void birthdayCannotBeBefore1900() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO shining_projects (student_id, birthday) VALUES ("
                    + STUDENT
                    + ", DATE '1899-12-31')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void bookingMustBeProposedAhead() {
    assertThatThrownBy(
            () -> insertBooking("CURRENT_TIMESTAMP - INTERVAL '1' HOUR", "pending", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void bookingCanOnlyCompleteAfterItStarts() {
    assertThatThrownBy(() ->
            insertBooking(
                "CURRENT_TIMESTAMP + INTERVAL '1' DAY",
                "completed",
                "CURRENT_TIMESTAMP + INTERVAL '1' DAY"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> insertBooking("CURRENT_TIMESTAMP + INTERVAL '1' DAY", "completed", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void prepScripturesAreLimitedTo100CharactersEach() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO booking_sessions (teacher_id, course_title, lecturer_name,"
                    + " proposed_at, prep_scriptures) VALUES ("
                    + TEACHER
                    + ", '聖經時觀', '張牧師', CURRENT_TIMESTAMP + INTERVAL '1' DAY, JSON_ARRAY('"
                    + "經".repeat(101)
                    + "'))"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertListenSession(String listenedAt, String notes) {
    jdbc.update(
        "INSERT INTO listen_sessions (student_id, course_id, lecturer_name, listened_at, notes)"
            + " VALUES ("
            + STUDENT
            + ", (SELECT MIN(id) FROM courses), '張牧師', "
            + listenedAt
            + ", ?)",
        notes);
  }

  private void insertBooking(String proposedAt, String status, String confirmedAt) {
    jdbc.update(
        "INSERT INTO booking_sessions (teacher_id, course_title, lecturer_name, proposed_at,"
            + " confirmed_at, status) VALUES ("
            + TEACHER
            + ", '聖經時觀', '張牧師', "
            + proposedAt
            + ", "
            + (confirmedAt == null ? "NULL" : confirmedAt)
            + ", ?)",
        status);
  }
}
