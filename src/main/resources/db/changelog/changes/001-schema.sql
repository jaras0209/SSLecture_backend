--liquibase formatted sql

-- ═════════════════════════════════════════════════════════════════════════════
-- 資料表（文件第 6 節）：22 張表與索引
-- 由前端 docs/database_schema.md（v1.16.0）產生，內容與文件相同；文件的 SQL 由前端 CI 在 MySQL 8.4 實測（scripts/check-schema.py）。
-- 上線後不修改已執行的 changeset：結構變更新增 changeset，並同步更新前端文件第 6 節。
-- ═════════════════════════════════════════════════════════════════════════════

--changeset sslecture:001-churches
CREATE TABLE churches (
  id         CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  name       VARCHAR(100) NOT NULL UNIQUE,
  region     VARCHAR(100),
  is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
--rollback DROP TABLE churches;

--changeset sslecture:001-users
CREATE TABLE users (
  id            CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  username      VARCHAR(30)  NOT NULL UNIQUE,
  password_hash VARCHAR(255),                 -- NULL = 只用社群登入
  role          ENUM('student', 'teacher', 'pastor', 'parent', 'admin') NOT NULL,
  church_id     CHAR(36),
  display_name  VARCHAR(20),
  real_name     VARCHAR(50),
  gender        ENUM('male', 'female'),       -- 只用於 student / teacher；NULL = 未設定
  email                VARCHAR(255),          -- 選填，小寫儲存
  email_verified       BOOLEAN NOT NULL DEFAULT FALSE,
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  avatar_url    VARCHAR(2048),                -- 只接受 https（見 2.1）
  login_method  ENUM('credentials', 'google', 'line') NOT NULL DEFAULT 'credentials',
  token_version INTEGER      NOT NULL DEFAULT 0,   -- 遞增即讓既有登入失效（見 5.15）
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
  last_login_at DATETIME(3),
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT users_username_format CHECK (REGEXP_LIKE(username, '^[a-z0-9._-]{3,30}$', 'c')),
  CONSTRAINT users_avatar_https    CHECK (REGEXP_LIKE(avatar_url, '^https://', 'c')),
  CONSTRAINT fk_users_church FOREIGN KEY (church_id) REFERENCES churches(id) ON DELETE SET NULL
);
CREATE INDEX idx_users_username    ON users(username);
CREATE INDEX idx_users_church_role ON users(church_id, role);
-- 信箱不分大小寫唯一；NULL 不受限制
CREATE UNIQUE INDEX uq_users_email ON users((LOWER(email)));
--rollback DROP TABLE users;

--changeset sslecture:001-courses
CREATE TABLE courses (
  id               VARCHAR(20)  PRIMARY KEY,
  title            VARCHAR(100) NOT NULL,
  category         ENUM('bible', 'lecture') NOT NULL,
  description      TEXT,
  cover_color      VARCHAR(200),
  duration_seconds INTEGER      NOT NULL DEFAULT 0,
  sort_order       INTEGER      NOT NULL DEFAULT 0,
  is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
--rollback DROP TABLE courses;

--changeset sslecture:001-lecturers
CREATE TABLE lecturers (
  id              CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  name            VARCHAR(50)  NOT NULL,
  title           VARCHAR(30)  NOT NULL,
  church_id       CHAR(36),
  linked_user_id  CHAR(36),                   -- NULL = 外來/自訂講師
  is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_lecturers_church      FOREIGN KEY (church_id)      REFERENCES churches(id) ON DELETE SET NULL,
  CONSTRAINT fk_lecturers_linked_user FOREIGN KEY (linked_user_id) REFERENCES users(id)    ON DELETE SET NULL
);
CREATE INDEX idx_lecturers_church ON lecturers(church_id);
-- 一個帳號只連結一筆講師資料；同教會不重複列同名同稱謂的講師（停用的不算，見 2.4）。
-- MySQL 沒有部分索引：停用的列讓運算式得到 NULL，而唯一索引不限制 NULL
CREATE UNIQUE INDEX uq_lecturers_linked_user ON lecturers((IF(is_active, linked_user_id, NULL)));
CREATE UNIQUE INDEX uq_lecturers_church_name ON lecturers((IF(is_active, church_id, NULL)), name, title);
--rollback DROP TABLE lecturers;

--changeset sslecture:001-lecturer-courses
CREATE TABLE lecturer_courses (
  lecturer_id CHAR(36)    NOT NULL,
  course_id   VARCHAR(20) NOT NULL,
  PRIMARY KEY (lecturer_id, course_id),
  CONSTRAINT fk_lecturer_courses_lecturer FOREIGN KEY (lecturer_id) REFERENCES lecturers(id) ON DELETE CASCADE,
  CONSTRAINT fk_lecturer_courses_course   FOREIGN KEY (course_id)   REFERENCES courses(id)   ON DELETE CASCADE
);
--rollback DROP TABLE lecturer_courses;

--changeset sslecture:001-listen-sessions
CREATE TABLE listen_sessions (
  id            CHAR(36)    PRIMARY KEY DEFAULT (UUID()),
  student_id    CHAR(36)    NOT NULL,
  course_id     VARCHAR(20) NOT NULL,
  lecturer_name VARCHAR(50) NOT NULL,
  listened_at   DATETIME(3) NOT NULL,
  notes         VARCHAR(2000),
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3),
  -- 聽課時間不可晚於寫入時間 1 小時以上（見 5.17）
  CONSTRAINT listened_not_in_future
    CHECK (listened_at <= COALESCE(updated_at, created_at) + INTERVAL 1 HOUR),
  CONSTRAINT fk_listen_sessions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT, -- 聽課紀錄保留為歷史
  CONSTRAINT fk_listen_sessions_course  FOREIGN KEY (course_id)  REFERENCES courses(id)
);
CREATE INDEX idx_listen_sessions_student        ON listen_sessions(student_id);
CREATE INDEX idx_listen_sessions_student_course ON listen_sessions(student_id, course_id);
CREATE INDEX idx_listen_sessions_listened_at    ON listen_sessions(listened_at DESC);
--rollback DROP TABLE listen_sessions;

--changeset sslecture:001-student-assignments
CREATE TABLE student_assignments (
  id         CHAR(36)    PRIMARY KEY DEFAULT (UUID()),
  student_id CHAR(36)    NOT NULL UNIQUE,
  teacher_id CHAR(36),
  pastor_id  CHAR(36),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_student_assignments_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_student_assignments_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_student_assignments_pastor  FOREIGN KEY (pastor_id)  REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_student_assignments_teacher ON student_assignments(teacher_id);
CREATE INDEX idx_student_assignments_pastor  ON student_assignments(pastor_id);
--rollback DROP TABLE student_assignments;

--changeset sslecture:001-parent-children
-- parent_children（家長綁定，多對多）
CREATE TABLE parent_children (
  parent_id  CHAR(36)    NOT NULL,
  student_id CHAR(36)    NOT NULL,
  bound_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (parent_id, student_id),
  CONSTRAINT fk_parent_children_parent  FOREIGN KEY (parent_id)  REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_parent_children_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_parent_children_student ON parent_children(student_id);
--rollback DROP TABLE parent_children;

--changeset sslecture:001-shining-projects
CREATE TABLE shining_projects (
  id               CHAR(36)    PRIMARY KEY DEFAULT (UUID()),
  student_id       CHAR(36)    NOT NULL UNIQUE,
  name             VARCHAR(50),
  birthday         DATE,                      -- 不晚於今天由 API 檢查（見 5.17）
  church_name      VARCHAR(100),
  school_grade     VARCHAR(50),
  custom_challenge VARCHAR(200),
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT shining_birthday_from_1900 CHECK (birthday >= '1900-01-01'),
  CONSTRAINT fk_shining_projects_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);
--rollback DROP TABLE shining_projects;

--changeset sslecture:001-shining-faith-checklists
CREATE TABLE shining_faith_checklists (
  id                  CHAR(36)    PRIMARY KEY DEFAULT (UUID()),
  shining_project_id  CHAR(36)    NOT NULL,
  category            ENUM('faith_phase1', 'faith_phase2', 'advanced_challenges') NOT NULL,
  item_key            VARCHAR(50) NOT NULL,
  is_checked          BOOLEAN     NOT NULL DEFAULT FALSE,
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE (shining_project_id, category, item_key),
  CONSTRAINT fk_faith_checklists_project FOREIGN KEY (shining_project_id) REFERENCES shining_projects(id) ON DELETE CASCADE
);
CREATE INDEX idx_faith_checklists_project ON shining_faith_checklists(shining_project_id);
--rollback DROP TABLE shining_faith_checklists;

--changeset sslecture:001-shining-lectures
CREATE TABLE shining_lectures (
  id                  CHAR(36)    PRIMARY KEY DEFAULT (UUID()),
  shining_project_id  CHAR(36)    NOT NULL,
  type                ENUM('character', 'coming_of_age') NOT NULL,
  theme_name          VARCHAR(50) NOT NULL,
  speaker_name        VARCHAR(50),
  lecture_date        DATE,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE (shining_project_id, type, theme_name),
  CONSTRAINT fk_shining_lectures_project FOREIGN KEY (shining_project_id) REFERENCES shining_projects(id) ON DELETE CASCADE
);
CREATE INDEX idx_shining_lectures_project ON shining_lectures(shining_project_id);
--rollback DROP TABLE shining_lectures;

--changeset sslecture:001-themes
CREATE TABLE themes (
  id         CHAR(36)    PRIMARY KEY DEFAULT (UUID()),
  church_id  CHAR(36)    NOT NULL,
  type       ENUM('character', 'coming_of_age') NOT NULL,
  name       VARCHAR(50) NOT NULL,
  sort_order INTEGER     NOT NULL DEFAULT 0,
  is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE (church_id, type, name),
  CONSTRAINT fk_themes_church FOREIGN KEY (church_id) REFERENCES churches(id) ON DELETE CASCADE
);
CREATE INDEX idx_themes_church_type ON themes(church_id, type);
--rollback DROP TABLE themes;

--changeset sslecture:001-page-restrictions
CREATE TABLE page_restrictions (
  id         CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  user_id    CHAR(36)     NOT NULL,
  page_path  VARCHAR(100) NOT NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE (user_id, page_path),
  CONSTRAINT fk_page_restrictions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_page_restrictions_user ON page_restrictions(user_id);
--rollback DROP TABLE page_restrictions;

--changeset sslecture:001-refresh-tokens
CREATE TABLE refresh_tokens (
  id            CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  user_id       CHAR(36)     NOT NULL,
  token_hash    VARCHAR(255) NOT NULL UNIQUE,
  remember_me   BOOLEAN      NOT NULL DEFAULT FALSE,
  token_version INTEGER      NOT NULL,        -- 簽發時的 users.token_version
  expires_at    DATETIME(3)  NOT NULL,        -- 閒置期限，換發時順延（見 2.13）
  is_revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_used_at  DATETIME(3),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user       ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
--rollback DROP TABLE refresh_tokens;

--changeset sslecture:001-teaching-stats
CREATE TABLE teaching_stats (
  id                    CHAR(36)    PRIMARY KEY DEFAULT (UUID()),
  teacher_id            CHAR(36)    NOT NULL,
  year                  SMALLINT    NOT NULL CHECK (year >= 2020),
  one_on_one_30         INTEGER     NOT NULL DEFAULT 0 CHECK (one_on_one_30 >= 0),
  one_to_many_30        INTEGER     NOT NULL DEFAULT 0 CHECK (one_to_many_30 >= 0),
  one_on_one_shining    INTEGER     NOT NULL DEFAULT 0 CHECK (one_on_one_shining >= 0),
  one_to_many_shining   INTEGER     NOT NULL DEFAULT 0 CHECK (one_to_many_shining >= 0),
  submitted_at          DATETIME(3),
  created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT uq_teaching_stats_teacher_year UNIQUE (teacher_id, year),
  CONSTRAINT fk_teaching_stats_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE RESTRICT -- 統計保留為歷史
);
CREATE INDEX idx_teaching_stats_year    ON teaching_stats(year);
CREATE INDEX idx_teaching_stats_teacher ON teaching_stats(teacher_id);
--rollback DROP TABLE teaching_stats;

--changeset sslecture:001-booking-sessions
CREATE TABLE booking_sessions (
  id                    CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  teacher_id            CHAR(36)     NOT NULL,
  church_id             CHAR(36),
  course_id             VARCHAR(20),
  course_title          VARCHAR(200) NOT NULL,
  lecturer_id           CHAR(36),
  lecturer_name         VARCHAR(50)  NOT NULL,
  lecturer_title        VARCHAR(30)  NOT NULL DEFAULT '',
  proposed_at           DATETIME(3)  NOT NULL,
  confirmed_at          DATETIME(3),
  duration_minutes      SMALLINT     CHECK (duration_minutes >= 1),
  status                VARCHAR(20)  NOT NULL DEFAULT 'pending'
                          CHECK (status IN ('pending','confirmed','completed','cancelled')),
  cancel_reason         VARCHAR(500),
  teacher_session_notes VARCHAR(2000),
  is_group_session      BOOLEAN      NOT NULL DEFAULT FALSE,
  prep_scriptures       JSON         NOT NULL DEFAULT (JSON_ARRAY()),  -- 字串陣列，每段最多 100 字
  prep_reading_notes    VARCHAR(2000),
  prep_materials        VARCHAR(500),
  created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT booking_prep_scriptures_array CHECK (JSON_SCHEMA_VALID(
    '{"type": "array", "items": {"type": "string", "maxLength": 100}}', prep_scriptures)),
  -- 預約的是還沒上的課；完成要等課開始之後（見 5.17）
  CONSTRAINT booking_proposed_ahead CHECK (proposed_at > created_at),
  CONSTRAINT booking_completed_after_start
    CHECK (status <> 'completed' OR (confirmed_at IS NOT NULL AND confirmed_at <= updated_at)),
  CONSTRAINT fk_booking_sessions_teacher  FOREIGN KEY (teacher_id)  REFERENCES users(id)     ON DELETE RESTRICT, -- 帳號軟刪除，預約保留為歷史
  CONSTRAINT fk_booking_sessions_church   FOREIGN KEY (church_id)   REFERENCES churches(id)  ON DELETE SET NULL,
  CONSTRAINT fk_booking_sessions_course   FOREIGN KEY (course_id)   REFERENCES courses(id)   ON DELETE SET NULL,
  CONSTRAINT fk_booking_sessions_lecturer FOREIGN KEY (lecturer_id) REFERENCES lecturers(id) ON DELETE SET NULL
);
CREATE INDEX idx_booking_sessions_teacher  ON booking_sessions(teacher_id);
CREATE INDEX idx_booking_sessions_lecturer ON booking_sessions(lecturer_id);
CREATE INDEX idx_booking_sessions_church   ON booking_sessions(church_id);
CREATE INDEX idx_booking_sessions_status   ON booking_sessions(status);
CREATE INDEX idx_booking_sessions_proposed ON booking_sessions(proposed_at DESC);
--rollback DROP TABLE booking_sessions;

--changeset sslecture:001-booking-attendees
CREATE TABLE booking_attendees (
  session_id               CHAR(36)     NOT NULL,
  student_id               CHAR(36)     NOT NULL,
  attendance_status        VARCHAR(20)  NOT NULL DEFAULT 'invited'
                             CHECK (attendance_status IN ('invited','attended','absent')),
  student_feedback         VARCHAR(1000),
  teacher_feedback         VARCHAR(1000),
  feedback_at              DATETIME(3),
  linked_listen_session_id CHAR(36),
  created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  -- 一場預約裡每位學員一筆：直接以 (場次, 學員) 當主鍵，主鍵也涵蓋依場次查詢
  PRIMARY KEY (session_id, student_id),
  CONSTRAINT fk_booking_attendees_session FOREIGN KEY (session_id) REFERENCES booking_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_booking_attendees_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT, -- 出席紀錄保留為歷史
  CONSTRAINT fk_booking_attendees_listen  FOREIGN KEY (linked_listen_session_id) REFERENCES listen_sessions(id) ON DELETE SET NULL
);
CREATE INDEX idx_booking_attendees_student ON booking_attendees(student_id);
CREATE INDEX idx_booking_attendees_status  ON booking_attendees(attendance_status);
--rollback DROP TABLE booking_attendees;

--changeset sslecture:001-invite-codes
CREATE TABLE invite_codes (
  code       VARCHAR(20)  PRIMARY KEY,
  role       ENUM('student', 'teacher', 'pastor', 'parent', 'admin') NOT NULL,
  church_id  CHAR(36),
  created_by CHAR(36),
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at DATETIME(3)  NOT NULL,
  used_by    CHAR(36),
  used_at    DATETIME(3),
  revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_invite_codes_church     FOREIGN KEY (church_id)  REFERENCES churches(id) ON DELETE SET NULL,
  CONSTRAINT fk_invite_codes_created_by FOREIGN KEY (created_by) REFERENCES users(id)    ON DELETE SET NULL,
  CONSTRAINT fk_invite_codes_used_by    FOREIGN KEY (used_by)    REFERENCES users(id)    ON DELETE SET NULL
);
CREATE INDEX idx_invite_codes_created_by ON invite_codes(created_by);
CREATE INDEX idx_invite_codes_used_by    ON invite_codes(used_by);
CREATE INDEX idx_invite_codes_expires_at ON invite_codes(expires_at);
CREATE INDEX idx_invite_codes_church     ON invite_codes(church_id);
--rollback DROP TABLE invite_codes;

--changeset sslecture:001-social-accounts
CREATE TABLE social_accounts (
  id                CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
  user_id           CHAR(36)     NOT NULL,
  provider          VARCHAR(20)  NOT NULL,
  provider_user_id  VARCHAR(255) NOT NULL,
  email             VARCHAR(255),
  display_name      VARCHAR(255),
  avatar_url        TEXT,
  access_token      TEXT,
  refresh_token     TEXT,
  token_expires_at  DATETIME(3),
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT uq_social_provider_uid UNIQUE (provider, provider_user_id),
  CONSTRAINT uq_user_provider       UNIQUE (user_id, provider),
  CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_social_accounts_user_id      ON social_accounts(user_id);
CREATE INDEX idx_social_accounts_provider_uid ON social_accounts(provider, provider_user_id);
CREATE INDEX idx_social_accounts_email        ON social_accounts(email);
--rollback DROP TABLE social_accounts;

--changeset sslecture:001-course-feedback
CREATE TABLE course_feedback (
  id         CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
  student_id CHAR(36)      NOT NULL,
  course_id  VARCHAR(20)   NOT NULL,
  author_id  CHAR(36)      NOT NULL,          -- 教師、牧者或管理員
  message    VARCHAR(1000) NOT NULL,
  created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT feedback_message_not_blank CHECK (CHAR_LENGTH(TRIM(message)) > 0),
  CONSTRAINT fk_course_feedback_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT, -- 保留為歷史
  CONSTRAINT fk_course_feedback_course  FOREIGN KEY (course_id)  REFERENCES courses(id),
  CONSTRAINT fk_course_feedback_author  FOREIGN KEY (author_id)  REFERENCES users(id) ON DELETE RESTRICT
);
CREATE INDEX idx_course_feedback_student_course ON course_feedback(student_id, course_id, created_at);
--rollback DROP TABLE course_feedback;

--changeset sslecture:001-course-feedback-reads
CREATE TABLE course_feedback_reads (
  student_id CHAR(36)    NOT NULL,
  course_id  VARCHAR(20) NOT NULL,
  seen_up_to DATETIME(3) NOT NULL,
  PRIMARY KEY (student_id, course_id),
  CONSTRAINT fk_feedback_reads_student FOREIGN KEY (student_id) REFERENCES users(id)   ON DELETE CASCADE,
  CONSTRAINT fk_feedback_reads_course  FOREIGN KEY (course_id)  REFERENCES courses(id) ON DELETE CASCADE
);
--rollback DROP TABLE course_feedback_reads;

--changeset sslecture:001-auth-attempts
CREATE TABLE auth_attempts (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  action       ENUM('login', 'invite_code', 'password_forgot', 'password_reset',
                    'password_change', 'oauth_exchange') NOT NULL,
  subject      VARCHAR(255),                  -- 帳號或信箱（小寫），不設外鍵（見 2.22）
  ip           VARCHAR(45)  NOT NULL,         -- IPv4 或 IPv6 的文字形式
  succeeded    BOOLEAN      NOT NULL,
  attempted_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE INDEX idx_auth_attempts_subject ON auth_attempts(action, subject, attempted_at);
CREATE INDEX idx_auth_attempts_ip      ON auth_attempts(action, ip, attempted_at);
--rollback DROP TABLE auth_attempts;
