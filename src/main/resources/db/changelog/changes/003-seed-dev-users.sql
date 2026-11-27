--liquibase formatted sql

-- ═════════════════════════════════════════════════════════════════════════════
-- 示範帳號（文件 7.3）：只在 dev、test 執行，密碼皆為 123456
-- 由前端 docs/database_schema.md 產生（SSLecture_front_end 的 scripts/gen-backend-changelog.py），內容與文件相同；
-- 文件的 SQL 由前端 CI 在 MySQL 8.4 實測（scripts/check-schema.py），前端 CI 也比對這些檔案與文件是否一致。
-- 上線後不修改已執行的 changeset：結構變更新增 changeset，並同步更新前端文件第 6 節。
-- ═════════════════════════════════════════════════════════════════════════════

--changeset sslecture:003-demo-accounts context:dev,test
-- password_hash 是 '123456' 的 bcrypt 雜湊（cost 12）
-- 示範教師與學員同為男性，教師才看得到學員（見 5.10）；家長綁定示範學員（見 2.19）
INSERT INTO users (username, password_hash, role, church_id, display_name, gender) VALUES
  ('student', '$2b$12$pnH2nqEGQ3cUteGYDpJWGulCSlFJGeE4ketApm7EiMHuj7BtPwxx6', 'student', (SELECT id FROM churches WHERE name='愛與話語'), 'SS學員',       'male'),
  ('teacher', '$2b$12$pnH2nqEGQ3cUteGYDpJWGulCSlFJGeE4ketApm7EiMHuj7BtPwxx6', 'teacher', (SELECT id FROM churches WHERE name='愛與話語'), '輔導教師',     'male'),
  ('pastor',  '$2b$12$pnH2nqEGQ3cUteGYDpJWGulCSlFJGeE4ketApm7EiMHuj7BtPwxx6', 'pastor',  (SELECT id FROM churches WHERE name='愛與話語'), '分區牧者',     NULL),
  ('parent',  '$2b$12$pnH2nqEGQ3cUteGYDpJWGulCSlFJGeE4ketApm7EiMHuj7BtPwxx6', 'parent',  (SELECT id FROM churches WHERE name='愛與話語'), '關懷家長',     NULL),
  ('admin',   '$2b$12$pnH2nqEGQ3cUteGYDpJWGulCSlFJGeE4ketApm7EiMHuj7BtPwxx6', 'admin',   NULL,                                            'SS中央管理員', NULL)
AS demo
ON DUPLICATE KEY UPDATE password_hash = demo.password_hash, role = demo.role, church_id = demo.church_id,
                        display_name = demo.display_name, gender = demo.gender, is_active = TRUE;

INSERT INTO parent_children (parent_id, student_id)
SELECT p.id, s.id FROM users p, users s WHERE p.username = 'parent' AND s.username = 'student'
ON DUPLICATE KEY UPDATE bound_at = parent_children.bound_at;
--rollback DELETE FROM parent_children; DELETE FROM users WHERE username IN ('student', 'teacher', 'pastor', 'parent', 'admin');
