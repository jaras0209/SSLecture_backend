# SSLecture Backend

SSLecture 前端（`../SSLecture`）的後端 API。規格以前端專案的文件為準：

- `docs/backend_api_spec.md`：API 規格
- `docs/database_schema.md`：資料庫設計
- `docs/oauth2_social_login_spec.md`：Google / LINE 社群登入

## 技術組合

| 項目 | 版本 / 套件 |
|------|-------------|
| Java | 25 |
| Spring Boot | 4.2.0-RC2（4.2.0 正式版發布後更新 `pom.xml` 的 parent 版本） |
| Web / 驗證 | `spring-boot-starter-webmvc`、`spring-boot-starter-validation` |
| 安全 | `spring-boot-starter-security`、`spring-boot-starter-security-oauth2-client`、JJWT 0.13 |
| 資料 | `spring-boot-starter-data-jpa`、Liquibase、MySQL 8.4（`mysql-connector-j`；開發、測試、正式都用 MySQL） |
| 其他 | Redis、Mail（規劃中）、Actuator、springdoc-openapi、Lombok、MapStruct |
| 測試 | JUnit 5、Spring Security Test、Testcontainers（MySQL 8.4） |
| 格式 | google-java-format 2.0.0（透過 Spotless） |

## 本地開發

需要 JDK 25（`JAVA_HOME` 指向它）與 Docker（本地的 MySQL 8.4）：

```bash
docker compose up -d      # 啟動 MySQL 8.4（compose.yaml），資料存在 Docker volume
./mvnw spring-boot:run
```

- 預設 profile 為 `dev`，Liquibase 會自動建表並灌入種子資料與示範帳號
- 開發用帳號：`student`、`teacher`、`pastor`、`parent`、`admin`，密碼都是 `123456`（只存在 dev / test）
- Swagger UI：http://localhost:8080/swagger-ui.html
- 健康檢查：http://localhost:8080/actuator/health
- 想清空資料重來：`docker compose down -v` 後再 `docker compose up -d`
- 不用 Docker 時：自己安裝 MySQL 8.4，建立 `sslecture` 資料庫與帳號，再以 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 指定

前端的 `.env.development` 已設定 `VITE_API_BASE_URL=http://localhost:8080`，CORS 預設允許 `http://localhost:5173`。

## Profiles

| Profile | 資料庫 | 用途 |
|---------|--------|------|
| `dev`（預設） | Docker 的 MySQL 8.4（`compose.yaml`） | 本地開發 |
| `test` | Testcontainers 啟動的 MySQL 8.4，或 `TEST_DB_URL` 指定的資料庫 | 自動化測試 |
| `prod` | MySQL 8.4 + Redis | 正式環境，環境變數見 `.env.example` |

## 資料庫（MySQL 8.4、Liquibase）

資料表的設計以前端的 [`docs/database_schema.md`](https://github.com/jaras0209/SSLecture_front_end/blob/main/docs/database_schema.md) 為準。
changelog 位於 `src/main/resources/db/changelog/`：

```
db.changelog-master.yaml           主檔：依序 include
changes/000-database-charset.sql   資料庫的字元集 utf8mb4、區分大小寫的定序 utf8mb4_0900_as_cs
changes/001-schema.sql             22 張表與索引（文件第 6 節），每張表一個 changeset
changes/002-seed-reference-data.sql  教會、課程、專題主題範例（文件 7.1，所有環境）
changes/003-seed-dev-users.sql     示範帳號（文件 7.3，只在 dev、test）
```

001–003 與文件的 SQL 相同；那份 SQL 由前端的 CI 在 MySQL 8.4 實際建表，並測試每個 `CHECK`、唯一限制與外鍵
（前端 `scripts/check-schema.py`）。

規則：

1. **已在正式環境執行的 changeset 不可修改**，要改結構就新增 `NNN-描述.sql` 並加到主檔，同時更新前端文件第 6 節
2. 只給開發用的資料加 `context:dev,test`；正式環境只跑 `prod` 與未標 context 的 changeset
3. 正式環境的第一個管理員不放進 changelog（密碼雜湊不進版本控制），上線時依文件 7.2 執行一次；
   或以前端的備份匯入工具搬移示範站的資料（文件第 9 節）

連線設定（`application.yml`）：連線時區一律 UTC（`DATETIME` 不記時區）、文字以 utf8mb4 傳送，
UUID 存成 `CHAR(36)` 字串。MySQL 的 `CHECK` 違反（錯誤碼 3819）等由 `sql-error-codes.xml` 歸類為
`DataIntegrityViolationException`（Spring 內建的對照沒有這幾個錯誤碼）。

### 為何開發與測試也用 MySQL

原本是「H2 開發 + PostgreSQL 正式」。改用 MySQL 後，資料表用到 MySQL 的運算式唯一索引、`REGEXP_LIKE`、
`JSON_SCHEMA_VALID`、區分大小寫的定序等，H2 的相容模式模擬不出來，以 H2 測試會測不到資料庫守住的規則，
所以開發與測試都在真正的 MySQL 8.4 上執行。

## 程式碼格式

使用 [google-java-format](https://github.com/google/google-java-format) 2.0.0：

```bash
./mvnw spotless:apply   # 自動排版
./mvnw spotless:check   # 只檢查（mvn verify 時會自動執行，格式不符則建置失敗）
```

IntelliJ 可安裝 google-java-format 外掛，存檔時就排好。

## Commit 規範

遵守 [Conventional Commits](https://www.conventionalcommits.org/zh-hant/v1.0.0/)：

```
<type>(<scope>): <描述>

feat(auth): 新增帳號密碼登入 API
fix(booking): 修正已取消場次仍可確認的問題
feat(api)!: 調整回應格式        ← ! 表示破壞性變更
```

`type`：`feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`

clone 後執行一次，讓 git 在 commit 時自動檢查格式：

```bash
git config core.hooksPath .githooks
```

## 測試與建置

```bash
./mvnw verify                      # 測試 + 格式檢查
./mvnw package -DskipTests         # 產出 target/sslecture-backend-*.jar
```

- 測試在 MySQL 8.4 上執行：有 Docker 時 Testcontainers 自動啟動 `mysql:8.4`；
  沒有 Docker 時以 `TEST_DB_URL`（及 `TEST_DB_USERNAME`、`TEST_DB_PASSWORD`）指定一個**專門給測試**的資料庫，
  測試開始時會清空它（`spring.liquibase.drop-first`）
- CI（`.github/workflows/ci.yml`）：每次推送與 PR 都以 GitHub Actions 的 MySQL 8.4 服務執行 `./mvnw verify`
