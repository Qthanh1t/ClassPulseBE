CREATE TABLE classrooms (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    subject     VARCHAR(100) NULL,
    join_code   VARCHAR(12)  NOT NULL,
    teacher_id  UUID         NOT NULL REFERENCES users (id),
    is_archived BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_classrooms_join_code ON classrooms (join_code);
CREATE        INDEX idx_classrooms_teacher   ON classrooms (teacher_id);

CREATE TABLE classroom_memberships (
    classroom_id UUID        NOT NULL REFERENCES classrooms (id),
    student_id   UUID        NOT NULL REFERENCES users (id),
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,

    PRIMARY KEY (classroom_id, student_id)
);

CREATE INDEX idx_memberships_student ON classroom_memberships (student_id);
