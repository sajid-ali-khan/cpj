-- Add new columns
ALTER TABLE leaderboard ADD COLUMN solved_count INT NOT NULL DEFAULT 0;
ALTER TABLE contests ADD COLUMN problem_count INT NOT NULL DEFAULT 0;
ALTER TABLE contests ADD COLUMN max_score INT NOT NULL DEFAULT 0;

-- Backfill existing contests
UPDATE contests c
SET problem_count = (
    SELECT COUNT(*) FROM contest_problems cp WHERE cp.contest_id = c.id
),
max_score = (
    SELECT COALESCE(SUM(cp.points), 0) FROM contest_problems cp WHERE cp.contest_id = c.id
);

-- Backfill existing leaderboard records
UPDATE leaderboard l
SET solved_count = (
    SELECT COUNT(DISTINCT s.problem_id)
    FROM submissions s
    WHERE s.user_id = l.user_id 
      AND s.contest_id = l.contest_id 
      AND s.verdict = 'ACCEPTED'
);
