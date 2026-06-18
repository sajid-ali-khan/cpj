-- Update any existing rows with 'NOT_STARTED' to 'NOT_REGISTERED'
UPDATE leaderboard SET status = 'NOT_REGISTERED' WHERE status = 'NOT_STARTED';

-- Alter the default value of status column in leaderboard
ALTER TABLE leaderboard ALTER COLUMN status SET DEFAULT 'NOT_REGISTERED';
