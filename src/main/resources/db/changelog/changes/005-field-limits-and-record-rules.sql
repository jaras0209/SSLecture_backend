--liquibase formatted sql

-- 對應前端規格 database_schema.md v1.9.0–v1.13.0：
--   5.16 文字欄位長度：資料庫欄位大小 = API 上限 = 前端 maxlength（src/utils/limits.ts）
--   5.17 已發生事件的日期：聽課時間、生日、預約時間的 CHECK
--   2.4  講師不重複（只建在 PostgreSQL，見最後兩個 changeset）
-- ALTER COLUMN ... SET DATA TYPE 是標準 SQL，H2 與 PostgreSQL 都接受，且保留 NOT NULL 與預設值。

--changeset sslecture:005-users-name-limits
ALTER TABLE users ALTER COLUMN display_name SET DATA TYPE VARCHAR(20);
ALTER TABLE users ALTER COLUMN real_name SET DATA TYPE VARCHAR(50);
--rollback ALTER TABLE users ALTER COLUMN display_name SET DATA TYPE VARCHAR(100);
--rollback ALTER TABLE users ALTER COLUMN real_name SET DATA TYPE VARCHAR(100);

--changeset sslecture:005-lecturer-name-limits
-- 講師名稱在各表一律 50，稱謂代碼一律 30
ALTER TABLE lecturers ALTER COLUMN name SET DATA TYPE VARCHAR(50);
ALTER TABLE listen_sessions ALTER COLUMN lecturer_name SET DATA TYPE VARCHAR(50);
ALTER TABLE booking_sessions ALTER COLUMN lecturer_name SET DATA TYPE VARCHAR(50);
ALTER TABLE booking_sessions ALTER COLUMN lecturer_title SET DATA TYPE VARCHAR(30);
ALTER TABLE shining_lectures ALTER COLUMN speaker_name SET DATA TYPE VARCHAR(50);
--rollback ALTER TABLE lecturers ALTER COLUMN name SET DATA TYPE VARCHAR(100);
--rollback ALTER TABLE listen_sessions ALTER COLUMN lecturer_name SET DATA TYPE VARCHAR(80);
--rollback ALTER TABLE booking_sessions ALTER COLUMN lecturer_name SET DATA TYPE VARCHAR(100);
--rollback ALTER TABLE booking_sessions ALTER COLUMN lecturer_title SET DATA TYPE VARCHAR(50);
--rollback ALTER TABLE shining_lectures ALTER COLUMN speaker_name SET DATA TYPE VARCHAR(80);

--changeset sslecture:005-theme-name-limits
ALTER TABLE themes ALTER COLUMN name SET DATA TYPE VARCHAR(50);
ALTER TABLE shining_lectures ALTER COLUMN theme_name SET DATA TYPE VARCHAR(50);
--rollback ALTER TABLE themes ALTER COLUMN name SET DATA TYPE VARCHAR(100);
--rollback ALTER TABLE shining_lectures ALTER COLUMN theme_name SET DATA TYPE VARCHAR(100);

--changeset sslecture:005-long-text-limits
-- 原本不限長度的 TEXT 改為有上限的 VARCHAR，讓資料庫也守住上限
ALTER TABLE listen_sessions ALTER COLUMN notes SET DATA TYPE VARCHAR(2000);
ALTER TABLE shining_projects ALTER COLUMN custom_challenge SET DATA TYPE VARCHAR(200);
ALTER TABLE booking_sessions ALTER COLUMN cancel_reason SET DATA TYPE VARCHAR(500);
ALTER TABLE booking_sessions ALTER COLUMN teacher_session_notes SET DATA TYPE VARCHAR(2000);
ALTER TABLE booking_sessions ALTER COLUMN prep_reading_notes SET DATA TYPE VARCHAR(2000);
ALTER TABLE booking_sessions ALTER COLUMN prep_materials SET DATA TYPE VARCHAR(500);
ALTER TABLE booking_sessions ALTER COLUMN prep_scriptures SET DATA TYPE ${varchar100_array};
ALTER TABLE booking_attendees ALTER COLUMN student_feedback SET DATA TYPE VARCHAR(1000);
ALTER TABLE booking_attendees ALTER COLUMN teacher_feedback SET DATA TYPE VARCHAR(1000);
ALTER TABLE course_feedback ALTER COLUMN message SET DATA TYPE VARCHAR(1000);
--rollback ALTER TABLE listen_sessions ALTER COLUMN notes SET DATA TYPE TEXT;
--rollback ALTER TABLE shining_projects ALTER COLUMN custom_challenge SET DATA TYPE TEXT;
--rollback ALTER TABLE booking_sessions ALTER COLUMN cancel_reason SET DATA TYPE TEXT;
--rollback ALTER TABLE booking_sessions ALTER COLUMN teacher_session_notes SET DATA TYPE TEXT;
--rollback ALTER TABLE booking_sessions ALTER COLUMN prep_reading_notes SET DATA TYPE TEXT;
--rollback ALTER TABLE booking_sessions ALTER COLUMN prep_materials SET DATA TYPE TEXT;
--rollback ALTER TABLE booking_sessions ALTER COLUMN prep_scriptures SET DATA TYPE ${text_array};
--rollback ALTER TABLE booking_attendees ALTER COLUMN student_feedback SET DATA TYPE TEXT;
--rollback ALTER TABLE booking_attendees ALTER COLUMN teacher_feedback SET DATA TYPE TEXT;
--rollback ALTER TABLE course_feedback ALTER COLUMN message SET DATA TYPE TEXT;

--changeset sslecture:005-record-date-checks
-- 聽課時間不可晚於寫入時間 1 小時以上；生日不早於 1900-01-01。
-- 「不晚於今天」會隨時間改變，不能寫在 CHECK 裡，由 API 檢查（見 5.17）
ALTER TABLE listen_sessions ADD CONSTRAINT listened_not_in_future
  CHECK (listened_at <= COALESCE(updated_at, created_at) + INTERVAL '1' HOUR);
ALTER TABLE shining_projects ADD CONSTRAINT ck_shining_projects_birthday
  CHECK (birthday >= DATE '1900-01-01');
--rollback ALTER TABLE listen_sessions DROP CONSTRAINT listened_not_in_future;
--rollback ALTER TABLE shining_projects DROP CONSTRAINT ck_shining_projects_birthday;

--changeset sslecture:005-booking-time-checks
-- 預約的是還沒上的課；完成要等課開始之後（見 5.17）
ALTER TABLE booking_sessions ADD CONSTRAINT booking_proposed_ahead
  CHECK (proposed_at > created_at);
ALTER TABLE booking_sessions ADD CONSTRAINT booking_completed_after_start
  CHECK (status <> 'completed' OR (confirmed_at IS NOT NULL AND confirmed_at <= updated_at));
--rollback ALTER TABLE booking_sessions DROP CONSTRAINT booking_proposed_ahead;
--rollback ALTER TABLE booking_sessions DROP CONSTRAINT booking_completed_after_start;

--changeset sslecture:005-lecturers-unique-postgresql dbms:postgresql
-- 一個帳號只連結一筆講師資料；同教會不重複列同名同稱謂的講師。停用的講師不算（見 2.4）。
-- H2 不支援部分索引（WHERE），本地開發與測試由 API 檢查（409 LECTURER_EXISTS / LECTURER_ACCOUNT_LINKED）
DROP INDEX idx_lecturers_linked_user;
CREATE UNIQUE INDEX uq_lecturers_linked_user ON lecturers(linked_user_id)
  WHERE linked_user_id IS NOT NULL AND is_active;
CREATE UNIQUE INDEX uq_lecturers_church_name ON lecturers(church_id, name, title) WHERE is_active;
--rollback DROP INDEX uq_lecturers_church_name;
--rollback DROP INDEX uq_lecturers_linked_user;
--rollback CREATE INDEX idx_lecturers_linked_user ON lecturers(linked_user_id);
