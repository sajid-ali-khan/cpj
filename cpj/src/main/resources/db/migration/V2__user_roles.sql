ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'STUDENT';

INSERT INTO users (name, roll_no, branch, role)
VALUES ('System Admin', 'ADMIN001', 'ADMIN', 'ADMIN')
ON CONFLICT (roll_no) DO NOTHING;
