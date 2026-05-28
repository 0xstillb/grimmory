CREATE TABLE IF NOT EXISTS koreader_metadata_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    book_file_id BIGINT,
    item_type VARCHAR(32) NOT NULL,
    dedupe_key VARCHAR(512) NOT NULL,
    source VARCHAR(32) NOT NULL,
    device VARCHAR(100),
    device_id VARCHAR(255),
    original_rating_value INTEGER,
    original_rating_scale INTEGER,
    normalized_rating_10 INTEGER,
    annotation_type VARCHAR(64),
    text VARCHAR(8000),
    note VARCHAR(8000),
    color VARCHAR(64),
    drawer VARCHAR(64),
    style VARCHAR(64),
    chapter VARCHAR(1024),
    page INTEGER,
    content_hash VARCHAR(128) NOT NULL,
    location_json CLOB,
    payload_json CLOB,
    client_created_at TIMESTAMP,
    client_updated_at TIMESTAMP,
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_koreader_metadata_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_koreader_metadata_book FOREIGN KEY (book_id) REFERENCES book(id) ON DELETE CASCADE,
    CONSTRAINT fk_koreader_metadata_book_file FOREIGN KEY (book_file_id) REFERENCES book_file(id) ON DELETE SET NULL,
    CONSTRAINT uk_koreader_metadata_user_book_type_dedupe UNIQUE (user_id, book_id, item_type, dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_koreader_metadata_user_book ON koreader_metadata_items (user_id, book_id);
CREATE INDEX IF NOT EXISTS idx_koreader_metadata_book_file ON koreader_metadata_items (book_file_id);
CREATE INDEX IF NOT EXISTS idx_koreader_metadata_item_type ON koreader_metadata_items (item_type);
CREATE INDEX IF NOT EXISTS idx_koreader_metadata_updated_at ON koreader_metadata_items (updated_at);
