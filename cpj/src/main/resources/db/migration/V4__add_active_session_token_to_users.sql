ALTER TABLE users
    ADD COLUMN active_session_token VARCHAR(255);

CREATE INDEX idx_users_session_token ON users(active_session_token);
