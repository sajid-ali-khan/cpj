-- ============================================================
-- V10 : Add violations column to leaderboard table
-- ============================================================

ALTER TABLE leaderboard ADD COLUMN violations INTEGER NOT NULL DEFAULT 0;
