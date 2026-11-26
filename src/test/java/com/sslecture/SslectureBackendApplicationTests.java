package com.sslecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 在 MySQL 上啟動整個應用程式，確認 Liquibase 建表、種子資料與約束都正常。 */
@SpringBootTest
@ActiveProfiles("test")
class SslectureBackendApplicationTests {

  @Autowired JdbcTemplate jdbc;

  @Test
  void contextLoads() {}

  @Test
  void liquibaseSeedsReferenceAndTestData() {
    assertThat(count("churches")).isEqualTo(19);
    assertThat(count("courses")).isEqualTo(30);
    assertThat(count("themes")).isEqualTo(8);
    // 其他測試會註冊新帳號，所以只算種子帳號
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username IN"
                    + " ('student', 'teacher', 'pastor', 'parent', 'admin')",
                Integer.class))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM parent_children pc JOIN users p ON p.id = pc.parent_id"
                    + " WHERE p.username = 'parent'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void usernameFormatIsEnforcedByDatabase() {
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO users (username, role) VALUES ('Bad Name', 'student')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void emailIsUniqueIgnoringCase() {
    jdbc.update(
        "INSERT INTO users (username, role, email) VALUES ('mail.one', 'student',"
            + " 'same@example.com')");
    try {
      assertThatThrownBy(() ->
              jdbc.update(
                  "INSERT INTO users (username, role, email) VALUES ('mail.two', 'student',"
                      + " 'Same@Example.com')"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.update("DELETE FROM users WHERE username = 'mail.one'");
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
