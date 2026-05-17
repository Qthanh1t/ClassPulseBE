-- Prevent multiple active sessions per classroom at DB level.
-- Partial unique index: only rows with status = 'active' participate,
-- so ended/waiting sessions are not affected.
CREATE UNIQUE INDEX idx_sessions_one_active_per_classroom
    ON sessions (classroom_id)
    WHERE status = 'active';
