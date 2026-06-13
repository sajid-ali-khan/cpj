-- ============================================================
-- V9 : Add email to users and create otp_verifications table
-- ============================================================

ALTER TABLE users ADD COLUMN email VARCHAR(255) UNIQUE;

CREATE TABLE otp_verifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    otp_code    VARCHAR(6) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_verifications_user ON otp_verifications(user_id);
