--liquibase formatted sql

-- ═════════════════════════════════════════════════════════════════════════════
-- 正式資料（文件 7.1）：教會、課程、專題主題範例。所有環境都執行
-- 由前端 docs/database_schema.md 產生（SSLecture_front_end 的 scripts/gen-backend-changelog.py），內容與文件相同；
-- 文件的 SQL 由前端 CI 在 MySQL 8.4 實測（scripts/check-schema.py），前端 CI 也比對這些檔案與文件是否一致。
-- 上線後不修改已執行的 changeset：結構變更新增 changeset，並同步更新前端文件第 6 節。
-- ═════════════════════════════════════════════════════════════════════════════

--changeset sslecture:002-churches
-- ────────────────────────────────────────────────
--  種子教會（19 間）
-- ────────────────────────────────────────────────
INSERT INTO churches (name) VALUES
  ('愛與話語'), ('主大明'), ('主勝利'), ('主生命'), ('主和睦光'),
  ('台北主話語'), ('聖靈'), ('永明'), ('主希望光'), ('實踐'),
  ('主愛'), ('主大永'), ('主磐石'), ('信主'), ('宜蘭主話語'),
  ('天民'), ('主幸福'), ('信榮'), ('主盼望');
--rollback DELETE FROM churches;

--changeset sslecture:002-courses
-- ────────────────────────────────────────────────
--  種子課程（30 堂）
-- ────────────────────────────────────────────────
INSERT INTO courses (id, title, category, duration_seconds, sort_order) VALUES
  ('bible-01',   '聖經時觀',               'bible',   180, 1),
  ('bible-02',   '日月停止',               'bible',   240, 2),
  ('bible-03',   '彼得與釣魚',             'bible',   200, 3),
  ('bible-04',   '以利亞與烏鴉飯',         'bible',   300, 4),
  ('bible-05',   '三分說',                 'bible',   220, 5),
  ('bible-06',   '七階段法則',             'bible',   180, 6),
  ('bible-07',   '火之概念',               'bible',   240, 7),
  ('bible-08',   '末世論',                 'bible',   200, 8),
  ('bible-09',   '比喻論',                 'bible',   300, 9),
  ('bible-10',   '洪水審判',               'bible',   220, 10),
  ('bible-11',   '無知中的相剋世界',       'bible',   180, 11),
  ('bible-12',   '預定論',                 'bible',   240, 12),
  ('bible-13',   '異端的概念',             'bible',   200, 13),
  ('bible-14',   '中心人物論',             'bible',   300, 14),
  ('bible-15',   '復活論',                 'bible',   220, 15),
  ('lecture-01', '三位一體',               'lecture', 180, 16),
  ('lecture-02', '聖子論',                 'lecture', 240, 17),
  ('lecture-03', '再臨論',                 'lecture', 200, 18),
  ('lecture-04', '空提論',                 'lecture', 300, 19),
  ('lecture-05', '啟示論',                 'lecture', 220, 20),
  ('lecture-06', '靈界論',                 'lecture', 180, 21),
  ('lecture-07', '撒旦論',                 'lecture', 240, 22),
  ('lecture-08', '該隱的個性',             'lecture', 200, 23),
  ('lecture-09', '罪與悔改',               'lecture', 300, 24),
  ('lecture-10', '創造目的',               'lecture', 220, 25),
  ('lecture-11', '墮落論',                 'lecture', 180, 26),
  ('lecture-12', '施洗約翰與耶穌的關係使命', 'lecture', 240, 27),
  ('lecture-13', '兩棵橄欖樹與兩個見證人', 'lecture', 200, 28),
  ('lecture-14', '歷史論',                 'lecture', 300, 29),
  ('lecture-15', '一載二載半載',           'lecture', 220, 30);
--rollback DELETE FROM courses;

--changeset sslecture:002-themes
-- ────────────────────────────────────────────────
--  種子主題（以愛與話語教會為例）
-- ────────────────────────────────────────────────
SET @church_id = (SELECT id FROM churches WHERE name = '愛與話語');

INSERT INTO themes (church_id, type, name, sort_order) VALUES
  (@church_id, 'character',     '品格力 - 自律', 1),
  (@church_id, 'character',     '品格力 - 感謝', 2),
  (@church_id, 'character',     '品格力 - 勇氣', 3),
  (@church_id, 'character',     '品格力 - 正直', 4),
  (@church_id, 'coming_of_age', '基督教歷史',     1),
  (@church_id, 'coming_of_age', '台灣攝理歷史',   2),
  (@church_id, 'coming_of_age', '情感教育',       3),
  (@church_id, 'coming_of_age', 'R的使命與精神',  4);
--rollback DELETE FROM themes;
