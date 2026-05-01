CREATE TABLE classroom_documents (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id    UUID         NOT NULL REFERENCES classrooms (id) ON DELETE CASCADE,
    uploader_id     UUID         NOT NULL REFERENCES users (id),
    file_name       VARCHAR(255) NOT NULL,
    storage_key     TEXT         NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    file_ext        VARCHAR(20)  NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_classroom ON classroom_documents (classroom_id);
