package com.sslecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 以 H2 啟動整個應用程式，確認 Liquibase 建表、種子資料與約束都正常。 */
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
    assertThat(count("users")).isEqualTo(5);
    assertThat(count("parent_children")).isEqualTo(1);
  }

  @Test
  void usernameFormatIsEnforcedByDatabase() {
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO users (username, role) VALUES ('Bad Name', 'student')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void emailMustBeStoredLowercase() {
    assertThatThrownBy(() ->
            jdbc.update(
                "INSERT INTO users (username, role, email) VALUES ('mail.test', 'student',"
                    + " 'Upper@Example.com')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }
}
