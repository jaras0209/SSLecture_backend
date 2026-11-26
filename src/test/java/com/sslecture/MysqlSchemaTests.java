package com.sslecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 資料庫與連線的設定（前端 docs/database_schema.md 第 6 節的設定表），測試用的 MySQL 也是這樣設定。
 *
 * <p>這些設定錯了不會立刻出錯，而是中文變亂碼、大小寫不同的名稱被當成重複、時間差 8 小時，所以在這裡檢查。
 */
@SpringBootTest
@ActiveProfiles("test")
class MysqlSchemaTests {

  @Autowired JdbcTemplate jdbc;

  @Test
  void runsOnMysqlWithEnforcedChecks() {
    String version = jdbc.queryForObject("SELECT VERSION()", String.class);
    // CHECK 從 8.0.16 起才會真正執行；正式環境為 8.4
    assertThat(version).matches("(8\\.0\\.(1[6-9]|[2-9]\\d)|8\\.[1-9]|9\\.).*");
  }

  @Test
  void tablesUseUtf8mb4AndCaseSensitiveCollation() {
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT table_collation FROM information_schema.tables"
                    + " WHERE table_schema = DATABASE() AND table_name <> 'DATABASECHANGELOG'"
                    + " AND table_name <> 'DATABASECHANGELOGLOCK'",
                String.class))
        .containsExactly("utf8mb4_0900_as_cs");
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM churches WHERE name = '愛與話語'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void connectionUsesUtc() {
    assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class))
        .isIn("+00:00", "UTC");
  }

  @Test
  void strictModeRefusesTextThatIsTooLong() {
    assertThat(jdbc.queryForObject("SELECT @@session.sql_mode", String.class))
        .contains("STRICT_TRANS_TABLES");
  }
}
