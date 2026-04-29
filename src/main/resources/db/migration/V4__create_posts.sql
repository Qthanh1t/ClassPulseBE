CREATE TABLE posts (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id UUID        NOT NULL REFERENCES classrooms (id) ON DELETE CASCADE,
    author_id    UUID        NOT NULL REFERENCES users (id),
    content      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_posts_classroom ON posts (classroom_id, created_at DESC);

CREATE TABLE post_attachments (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    file_name       VARCHAR(255) NOT NULL,
    storage_key     TEXT         NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    file_ext        VARCHAR(20)  NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_post ON post_attachments (post_id);
