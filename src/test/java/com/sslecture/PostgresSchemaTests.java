package com.sslecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 在真正的 PostgreSQL 上跑一次 Liquibase，確認 changelog 不只在 H2 能用。
 *
 * <p>需要 Docker；沒有 Docker 時自動略過（本地開發以 H2 為主，正式環境是 PostgreSQL）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class PostgresSchemaTests {

  @Autowired JdbcTemplate jdbc;

  @Test
  void liquibaseRunsOnPostgres() {
    assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM courses", Integer.class)).isEqualTo(30);
  }
}
