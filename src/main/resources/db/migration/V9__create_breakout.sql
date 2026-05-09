CREATE TABLE breakout_sessions
(
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL REFERENCES sessions (id),
    started_at TIMESTAMPTZ NOT NULL    DEFAULT now(),
    ended_at   TIMESTAMPTZ
);

CREATE INDEX idx_breakout_sessions_session ON breakout_sessions (session_id);

-- ─────────────────────────────────────────────────────────────

CREATE TABLE breakout_rooms
(
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    breakout_session_id UUID         NOT NULL REFERENCES breakout_sessions (id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    task                TEXT,
    room_order          SMALLINT     NOT NULL
);

CREATE INDEX idx_rooms_breakout_session ON breakout_rooms (breakout_session_id);

-- ─────────────────────────────────────────────────────────────

CREATE TABLE breakout_assignments
(
    room_id    UUID NOT NULL REFERENCES breakout_rooms (id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users (id),
    PRIMARY KEY (room_id, student_id)
);

CREATE INDEX idx_breakout_assign_student ON breakout_assignments (student_id);
