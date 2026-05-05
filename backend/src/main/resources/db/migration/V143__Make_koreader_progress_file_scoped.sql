ALTER TABLE koreader_progress
    DROP INDEX IF EXISTS uk_koreader_progress_user_book;

ALTER TABLE koreader_progress
    ADD COLUMN IF NOT EXISTS book_file_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS current_hash VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS initial_hash VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS source VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS progress_version BIGINT NULL;

ALTER TABLE koreader_progress
    ADD CONSTRAINT fk_koreader_progress_book_file
    FOREIGN KEY (book_file_id) REFERENCES book_file (id) ON DELETE SET NULL;

ALTER TABLE koreader_progress
    ADD CONSTRAINT uk_koreader_progress_user_book_file
    UNIQUE (user_id, book_file_id);

CREATE INDEX IF NOT EXISTS idx_koreader_progress_book_file ON koreader_progress (book_file_id);
CREATE INDEX IF NOT EXISTS idx_koreader_progress_source ON koreader_progress (source);
