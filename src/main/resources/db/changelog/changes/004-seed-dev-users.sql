--liquibase formatted sql

-- 本地開發 / 測試用帳號，只在 dev、test context 執行，正式環境不會建立。
-- 五個帳號的密碼都是 123456（BCrypt cost 12）。
-- 示範教師與學員同為男性，教師才看得到學員（見 database_schema.md 5.10）；家長綁定示範學員（見 2.19）

--changeset sslecture:004-seed-dev-users context:dev,test
INSERT INTO users (username, password_hash, role, church_id, display_name, gender) VALUES
  ('student', '$2a$12$.LbUsYMpfRko6gP61O19FO2xnYmBcRUI/rTVcMoSrCVBD0DbUYlgy', 'student', (SELECT id FROM churches WHERE name = '愛與話語'), 'SS學員',       'male'),
  ('teacher', '$2a$12$.LbUsYMpfRko6gP61O19FO2xnYmBcRUI/rTVcMoSrCVBD0DbUYlgy', 'teacher', (SELECT id FROM churches WHERE name = '愛與話語'), '輔導教師',     'male'),
  ('pastor',  '$2a$12$.LbUsYMpfRko6gP61O19FO2xnYmBcRUI/rTVcMoSrCVBD0DbUYlgy', 'pastor',  (SELECT id FROM churches WHERE name = '愛與話語'), '分區牧者',     NULL),
  ('parent',  '$2a$12$.LbUsYMpfRko6gP61O19FO2xnYmBcRUI/rTVcMoSrCVBD0DbUYlgy', 'parent',  (SELECT id FROM churches WHERE name = '愛與話語'), '關懷家長',     NULL),
  ('admin',   '$2a$12$.LbUsYMpfRko6gP61O19FO2xnYmBcRUI/rTVcMoSrCVBD0DbUYlgy', 'admin',   NULL,                                               'SS中央管理員', NULL);

INSERT INTO parent_children (parent_id, student_id)
SELECT p.id, s.id FROM users p, users s WHERE p.username = 'parent' AND s.username = 'student';

INSERT INTO student_assignments (student_id, teacher_id, pastor_id)
SELECT s.id, t.id, p.id FROM users s, users t, users p
WHERE s.username = 'student' AND t.username = 'teacher' AND p.username = 'pastor';
--rollback DELETE FROM student_assignments; DELETE FROM parent_children; DELETE FROM users WHERE username IN ('student', 'teacher', 'pastor', 'parent', 'admin');
