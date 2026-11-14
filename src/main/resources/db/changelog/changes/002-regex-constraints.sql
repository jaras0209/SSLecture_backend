--liquibase formatted sql

-- 帳號格式檢查：小寫英數與 . _ -，3–30 字元（見 database_schema.md 2.1）
-- 正規表示式語法兩種資料庫不同，所以各寫一份

--changeset sslecture:002-users-username-format-postgresql dbms:postgresql
ALTER TABLE users ADD CONSTRAINT ck_users_username_format CHECK (username ~ '^[a-z0-9._-]{3,30}$');
--rollback ALTER TABLE users DROP CONSTRAINT ck_users_username_format;

--changeset sslecture:002-users-username-format-h2 dbms:h2
ALTER TABLE users ADD CONSTRAINT ck_users_username_format CHECK (REGEXP_LIKE(username, '^[a-z0-9._-]{3,30}$'));
--rollback ALTER TABLE users DROP CONSTRAINT ck_users_username_format;
