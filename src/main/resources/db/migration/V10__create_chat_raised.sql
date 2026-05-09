CREATE TABLE chat_messages
(
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID        NOT NULL REFERENCES sessions (id),
    sender_id        UUID        NOT NULL REFERENCES users (id),
    content          TEXT        NOT NULL,
    breakout_room_id UUID        REFERENCES breakout_rooms (id),
    sent_at          TIMESTAMPTZ NOT NULL    DEFAULT now()
);

CREATE INDEX idx_chat_session_time ON chat_messages (session_id, sent_at);
CREATE INDEX idx_chat_breakout ON chat_messages (breakout_room_id) WHERE breakout_room_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────

CREATE TABLE raised_hands
(
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID        NOT NULL REFERENCES sessions (id),
    student_id UUID        NOT NULL REFERENCES users (id),
    raised     BOOLEAN     NOT NULL,
    event_at   TIMESTAMPTZ NOT NULL    DEFAULT now()
);

CREATE INDEX idx_raised_hands_session ON raised_hands (session_id);
