--liquibase formatted sql

-- ═════════════════════════════════════════════════════════════════════════════
-- 資料庫的字元集與定序（前端 docs/database_schema.md 第 6 節的設定表）。
-- 資料表沿用資料庫的預設，所以要在建表之前設定：
--   * utf8mb4：中文與表情符號都存得下
--   * utf8mb4_0900_as_cs：唯一限制與比對區分大小寫，和前端一致（MySQL 預設的 _ai_ci 會把 John 與 john 當成重複）
-- 不論資料庫是怎麼建立的（Docker 映像檔、雲端主機的預設值），都由這個 changeset 統一。
-- ═════════════════════════════════════════════════════════════════════════════

--changeset sslecture:000-database-charset
ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
--rollback ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
