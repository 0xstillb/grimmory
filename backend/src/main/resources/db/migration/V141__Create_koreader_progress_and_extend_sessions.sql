CREATE TABLE IF NOT EXISTS koreader_progress
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    book_id          BIGINT        NOT NULL,
    book_hash        VARCHAR(128)  NOT NULL,
    document         VARCHAR(512)  NULL,
    file_format      VARCHAR(32)   NULL,
    progress         VARCHAR(2000) NULL,
    location         VARCHAR(2000) NULL,
    percentage       FLOAT         NULL,
    current_page     INTEGER       NULL,
    total_pages      INTEGER       NULL,
    device           VARCHAR(100)  NULL,
    device_id        VARCHAR(255)  NULL,
    client_timestamp DATETIME      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_koreader_progress_user_book UNIQUE (user_id, book_id),
    CONSTRAINT fk_koreader_progress_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_koreader_progress_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_koreader_progress_user_hash ON koreader_progress (user_id, book_hash);
CREATE INDEX IF NOT EXISTS idx_koreader_progress_book ON koreader_progress (book_id);
CREATE INDEX IF NOT EXISTS idx_koreader_progress_updated_at ON koreader_progress (updated_at);

ALTER TABLE reading_sessions
    ADD COLUMN IF NOT EXISTS book_hash VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS device VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS idx_reading_session_user_book_hash ON reading_sessions (user_id, book_hash, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_reading_session_user_device ON reading_sessions (user_id, device_id, start_time DESC);
