CREATE TABLE questions
(
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id     UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    question_order SMALLINT    NOT NULL,
    type           VARCHAR(20) NOT NULL
        CHECK (type IN ('single', 'multiple', 'essay')),
    content        TEXT        NOT NULL,
    timer_seconds  INTEGER,
    status         VARCHAR(20) NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'running', 'ended')),
    started_at     TIMESTAMPTZ,
    ends_at        TIMESTAMPTZ,
    ended_at       TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_questions_session ON questions (session_id, question_order);
CREATE INDEX idx_questions_running ON questions (session_id) WHERE status = 'running';

-- ─────────────────────────────────────────────────────────────

CREATE TABLE question_options
(
    id           UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id  UUID       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    label        VARCHAR(5) NOT NULL,
    text         TEXT       NOT NULL,
    is_correct   BOOLEAN    NOT NULL,
    option_order SMALLINT   NOT NULL
);

CREATE INDEX idx_options_question ON question_options (question_id);

-- ─────────────────────────────────────────────────────────────

CREATE TABLE student_answers
(
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id         UUID        NOT NULL REFERENCES questions (id),
    student_id          UUID        NOT NULL REFERENCES users (id),
    selected_option_ids UUID[],
    essay_text          TEXT,
    confidence          VARCHAR(10) CHECK (confidence IN ('low', 'medium', 'high')),
    is_correct          BOOLEAN,
    answered_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (question_id, student_id)
);

CREATE INDEX idx_answers_question ON student_answers (question_id);
CREATE INDEX idx_answers_student ON student_answers (student_id);
