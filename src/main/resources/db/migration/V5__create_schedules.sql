CREATE TABLE schedules
(
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id   UUID         NOT NULL REFERENCES classrooms (id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    scheduled_date DATE         NOT NULL,
    start_time     TIME         NOT NULL,
    end_time       TIME         NOT NULL,
    description    TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_schedules_classroom_date ON schedules (classroom_id, scheduled_date);
