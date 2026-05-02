CREATE TABLE IF NOT EXISTS koreader_annotations
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    book_id             BIGINT        NOT NULL,
    dedupe_key          VARCHAR(128)  NOT NULL,
    koreader_pos        VARCHAR(1000) NULL,
    page                INTEGER       NULL,
    chapter             VARCHAR(500)  NULL,
    text                VARCHAR(5000) NULL,
    note                VARCHAR(5000) NULL,
    color               VARCHAR(32)   NULL,
    drawer              VARCHAR(32)   NULL,
    source              VARCHAR(32)   NOT NULL DEFAULT 'KOREADER',
    koreader_created_at BIGINT        NULL,
    koreader_updated_at BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_koreader_annotations_user_book_key UNIQUE (user_id, book_id, dedupe_key),
    CONSTRAINT fk_koreader_annotations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_koreader_annotations_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_koreader_annotations_user_book
    ON koreader_annotations (user_id, book_id);

CREATE INDEX IF NOT EXISTS idx_koreader_annotations_user_book_updated_at
    ON koreader_annotations (user_id, book_id, updated_at);

CREATE TABLE IF NOT EXISTS koreader_bookmarks
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    book_id             BIGINT        NOT NULL,
    dedupe_key          VARCHAR(128)  NOT NULL,
    koreader_pos        VARCHAR(1000) NULL,
    page                INTEGER       NULL,
    chapter             VARCHAR(500)  NULL,
    text                VARCHAR(5000) NULL,
    note                VARCHAR(5000) NULL,
    source              VARCHAR(32)   NOT NULL DEFAULT 'KOREADER',
    koreader_created_at BIGINT        NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_koreader_bookmarks_user_book_key UNIQUE (user_id, book_id, dedupe_key),
    CONSTRAINT fk_koreader_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_koreader_bookmarks_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_koreader_bookmarks_user_book
    ON koreader_bookmarks (user_id, book_id);

CREATE INDEX IF NOT EXISTS idx_koreader_bookmarks_user_book_updated_at
    ON koreader_bookmarks (user_id, book_id, updated_at);
