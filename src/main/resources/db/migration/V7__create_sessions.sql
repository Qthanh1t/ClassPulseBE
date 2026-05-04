CREATE TABLE sessions
(
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id UUID        NOT NULL REFERENCES classrooms (id) ON DELETE CASCADE,
    schedule_id  UUID        REFERENCES schedules (id) ON DELETE SET NULL,
    teacher_id   UUID        NOT NULL REFERENCES users (id),
    status       VARCHAR(20) NOT NULL    DEFAULT 'waiting'
        CHECK (status IN ('waiting', 'active', 'ended')),
    started_at   TIMESTAMPTZ,
    ended_at     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL    DEFAULT now()
);

CREATE INDEX idx_sessions_classroom ON sessions (classroom_id);
CREATE INDEX idx_sessions_status ON sessions (status) WHERE status = 'active';

-- ─────────────────────────────────────────────────────────────

CREATE TABLE session_presences
(
    session_id UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    student_id UUID        NOT NULL REFERENCES users (id),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at    TIMESTAMPTZ,
    PRIMARY KEY (session_id, student_id)
);

CREATE INDEX idx_presences_session ON session_presences (session_id);

-- ─────────────────────────────────────────────────────────────

CREATE TABLE session_student_summaries
(
    session_id      UUID         NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    student_id      UUID         NOT NULL REFERENCES users (id),
    total_questions SMALLINT     NOT NULL,
    answered_count  SMALLINT     NOT NULL,
    correct_count   SMALLINT     NOT NULL,
    skipped_count   SMALLINT     NOT NULL,
    score_percent   NUMERIC(5,2) NOT NULL,
    computed_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, student_id)
);

CREATE INDEX idx_summaries_session ON session_student_summaries (session_id);
