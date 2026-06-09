-- ============================================================
-- V1 : Initial schema for CPJ (Competitive Programming Judge)
-- ============================================================

CREATE TABLE users (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    roll_no VARCHAR(20)  NOT NULL UNIQUE,
    branch  VARCHAR(50)
);

CREATE TABLE problems (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    constraints TEXT,
    difficulty  VARCHAR(20),   -- EASY | MEDIUM | HARD
    media_link  VARCHAR(500)
);

CREATE TABLE test_cases (
    id              BIGSERIAL PRIMARY KEY,
    problem_id      BIGINT  NOT NULL REFERENCES problems(id),
    stdin           TEXT,
    expected_output TEXT    NOT NULL,
    is_sample       BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE contests (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    start_time    TIMESTAMP    NOT NULL,
    duration_mins INTEGER      NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'UPCOMING'  -- UPCOMING | ONGOING | ENDED
);

CREATE TABLE contest_problems (
    contest_id    BIGINT  NOT NULL REFERENCES contests(id),
    problem_id    BIGINT  NOT NULL REFERENCES problems(id),
    points        INTEGER NOT NULL DEFAULT 100,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (contest_id, problem_id)
);

CREATE TABLE submissions (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    contest_id   BIGINT      NOT NULL REFERENCES contests(id),
    problem_id   BIGINT      NOT NULL REFERENCES problems(id),
    code         TEXT        NOT NULL,
    language_id  INTEGER     NOT NULL,
    judge0_token VARCHAR(100),
    verdict      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    time_ms      INTEGER,
    memory_kb    INTEGER,
    submitted_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE leaderboard (
    id          BIGSERIAL PRIMARY KEY,
    contest_id  BIGINT    NOT NULL REFERENCES contests(id),
    user_id     BIGINT    NOT NULL REFERENCES users(id),
    score       INTEGER   NOT NULL DEFAULT 0,
    last_ac_time TIMESTAMP,
    UNIQUE (contest_id, user_id)
);

-- ---- Indexes for hot query paths ----
CREATE INDEX idx_test_cases_problem     ON test_cases(problem_id);
CREATE INDEX idx_submissions_user_contest ON submissions(user_id, contest_id);
CREATE INDEX idx_submissions_token      ON submissions(judge0_token);
CREATE INDEX idx_leaderboard_ranking    ON leaderboard(contest_id, score DESC, last_ac_time ASC NULLS LAST);
