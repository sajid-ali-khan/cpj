-- ============================================================
-- V8 : Remove status column and convert timestamps to TIMESTAMPTZ
-- ============================================================

ALTER TABLE contests DROP COLUMN status;

ALTER TABLE contests ALTER COLUMN start_time TYPE TIMESTAMPTZ;
ALTER TABLE submissions ALTER COLUMN submitted_at TYPE TIMESTAMPTZ;
ALTER TABLE leaderboard ALTER COLUMN last_ac_time TYPE TIMESTAMPTZ;
