# GrimmLink — Backend Changes Summary

GrimmLink is a KOReader companion backend providing book matching, reading progress sync, shelf management, and a PDF progress bridge. This document summarizes the backend changes required to support it.

## Architecture Overview

```
KOReader Device                         Grimmory Backend
┌──────────────┐    x-auth-user/key    ┌──────────────────────────────────────────┐
│  KOReader    │ ──────────────────→   │  KoreaderAuthFilter                      │
│  (EPUB/PDF)  │    /api/koreader/**   │  ↓                                       │
└──────────────┘                       │  KoreaderController                      │
                                       │  KoreaderShelfController                 │
┌──────────────┐    JWT / session      │  KoreaderWebReaderBridgeController       │
│  Web Reader  │ ──────────────────→   │  ↓                                       │
│  (PDF only)  │    /api/koreader/**   │  KoreaderService                         │
└──────────────┘                       │  ↓                                       │
                                       │  KoreaderProgressEntity (primary)        │
┌──────────────┐    JWT                │  UserBookProgressEntity (legacy)         │
│  Grimmory UI │ ──────────────────→   │  ↓                                       │
│  (Settings)  │    /api/v1/koreader-* │  KoreaderUserController                  │
└──────────────┘                       └──────────────────────────────────────────┘
```

## New Files (Added)

### Controllers

| File | Base Path | Endpoints | Purpose |
|:-----|:----------|:----------|:--------|
| `KoreaderController` | `/api/koreader` | `GET /users/auth`, `PUT /syncs/progress`, `GET /syncs/progress/{hash}`, `GET /books/by-hash/{hash}` | Core KOReader sync protocol |
| `KoreaderUserController` | `/api/v1/koreader-users` | `GET /me`, `PUT /me`, `PATCH /me/sync`, `PATCH /me/sync-progress-with-booklore` | User account linking and sync preferences |
| `KoreaderShelfController` | `/api/koreader` | `GET /shelves`, `GET /shelves/{id}/books`, `GET /books/{id}/download`, `POST /shelves/{id}/books/{id}/remove` | Shelf browsing and book downloads |
| `KoreaderWebReaderBridgeController` | `/api/koreader/books/{id}` | `GET /pdf-progress`, `PUT /pdf-progress` | PDF progress bridge with conflict detection |

### Services

| File | Purpose |
|:-----|:--------|
| `KoreaderService` | Core progress sync — hash matching, percentage normalization, dual-write to KoreaderProgress + legacy UserBookProgress |
| `KoreaderUserService` | User CRUD — links KoreaderUser to BookLoreUser, MD5 password hashing |
| `KoreaderShelfService` | Shelf listing, book browsing, file download, shelf membership removal |
| `KoreaderSecurityContextService` | Authentication bridge — resolves authenticated reader from JWT or KoreaderUserDetails |

### Entities

| Entity | Table | Key Fields |
|:-------|:------|:-----------|
| `KoreaderUserEntity` | `koreader_user` | `username`, `passwordMD5`, `syncEnabled`, `syncWithBookloreReader`, FK→`users.id` |
| `KoreaderProgressEntity` | `koreader_progress` | `bookHash`, `document`, `progress`, `location`, `percentage`, `currentPage`, `totalPages`, `device`, `deviceId`, `clientTimestamp`, FK→`users.id`, FK→`book.id`, FK→`book_file.id` |

### Repositories

| Repository | Custom Queries |
|:-----------|:---------------|
| `KoreaderUserRepository` | `findByUsername()`, `findByBookLoreUserId()` |
| `KoreaderProgressRepository` | `findByUserIdAndBookFileId()`, `findByUserIdAndBookIdAndBookHash()`, `findMostRecentByUserIdAndBookId()` |

### DTOs

| DTO | Purpose |
|:----|:--------|
| `KoreaderProgress` | Request/response for progress sync — includes `bookHash`, `progress`, `percentage`, `device`, `conflictDetected`, `force` flag |
| `KoreaderUser` | User profile DTO |
| `KoreaderShelfSummary` | Shelf listing response |
| `KoreaderBookSummary` | Book listing with `bookId`, `title`, `author`, `fileName`, `fileFormat`, `bookHash` |
| `KoreaderShelfRemovalResponse` | Shelf removal confirmation |

### Security

| File | Purpose |
|:-----|:--------|
| `KoreaderAuthFilter` | `OncePerRequestFilter` for `/api/koreader/**` — authenticates via `x-auth-user` + `x-auth-key` (MD5) headers |
| `KoreaderUserDetails` | Custom `UserDetails` holding sync flags and `bookLoreUserId` for linking |

### Utilities

| File | Purpose |
|:-----|:--------|
| `EpubCfiService` | EPUB CFI ↔ XPointer conversion with document caching (5-min TTL) |
| `JsoupDocumentNavigator` | EPUB document navigation for CFI resolution |

### Mapper

| File | Purpose |
|:-----|:--------|
| `KoreaderUserMapper` | MapStruct mapper for KoreaderUser ↔ KoreaderUserEntity |

## Modified Files (Upstream Changes)

| File | Change |
|:-----|:-------|
| `SecurityConfig` | Added `koreaderSecurityChain` bean (Order=3) — stateless, CSRF disabled, KoreaderAuthFilter |
| `SecurityUtil` | Added `canSyncKoReader()` permission check |
| `UserPermission` | Added `SYNC_KOREADER` enum value |
| `UserPermissionsEntity` | Added `permission_sync_koreader` column |
| `UserBookProgressEntity` | Added legacy bridge columns: `koreaderProgress`, `koreaderProgressPercent`, `koreaderDevice`, `koreaderDeviceId`, `koreaderLastSyncTime` |
| `ReadingSessionEntity` | Added KOReader metadata: `bookHash`, `device`, `deviceId`, `currentPage`, `totalPages` |
| `ReadingSessionService` | Added `KoreaderSecurityContextService` dependency for KOReader-authenticated batch uploads |
| `HardcoverSyncSettingsController` | Extended permission check to include `canSyncKoReader()` |

## Database Migrations

### V42 — KOReader User Table and Sync Columns

```sql
CREATE TABLE koreader_user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(100) UNIQUE NOT NULL,
    password     VARCHAR(255) NOT NULL,
    password_md5 VARCHAR(255) NOT NULL,
    sync_enabled BOOLEAN DEFAULT FALSE,
    booklore_user_id BIGINT REFERENCES users(id),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

ALTER TABLE user_book_progress ADD koreader_progress VARCHAR(1000);
ALTER TABLE user_book_progress ADD koreader_progress_percent FLOAT;
ALTER TABLE user_book_progress ADD koreader_device VARCHAR(100);
ALTER TABLE user_book_progress ADD koreader_device_id VARCHAR(100);
ALTER TABLE user_book_progress ADD koreader_last_sync_time TIMESTAMP;

ALTER TABLE user_permissions ADD permission_sync_koreader BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE user_permissions SET permission_sync_koreader = TRUE WHERE ... (admins);
```

### V94 — Sync with Web Reader Flag

```sql
ALTER TABLE koreader_user ADD sync_with_booklore_reader BOOLEAN NOT NULL DEFAULT FALSE;
```

### V144 — Dedicated Progress Table and Reading Session Metadata

```sql
CREATE TABLE koreader_progress (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id          BIGINT NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    book_file_id     BIGINT REFERENCES book_file(id) ON DELETE SET NULL,
    book_hash        VARCHAR(128) NOT NULL,
    document         VARCHAR(512),
    file_format      VARCHAR(32),
    progress         VARCHAR(2000),
    location         VARCHAR(2000),
    percentage       FLOAT,
    current_page     INTEGER,
    total_pages      INTEGER,
    device           VARCHAR(100),
    device_id        VARCHAR(255),
    client_timestamp DATETIME,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_file_id),
    UNIQUE (user_id, book_id, book_hash)
);

CREATE INDEX idx_koreader_progress_user_hash ON koreader_progress(user_id, book_hash);
CREATE INDEX idx_koreader_progress_book ON koreader_progress(book_id);

ALTER TABLE reading_sessions ADD book_hash VARCHAR(128);
ALTER TABLE reading_sessions ADD device VARCHAR(100);
ALTER TABLE reading_sessions ADD device_id VARCHAR(255);
ALTER TABLE reading_sessions ADD current_page INTEGER;
ALTER TABLE reading_sessions ADD total_pages INTEGER;
```

## Key Design Decisions

### 1. Dual Progress Tracking
KOReader progress is stored in `koreader_progress` (primary, full fidelity) and mirrored to `user_book_progress` (legacy, for web UI display). This avoids breaking the existing reading progress UI while storing complete KOReader data.

### 2. Hash-Based Book Matching
Books are matched by file hash (`currentHash` then `initialHash` on `book_file`). This enables device-to-cloud sync without requiring books to be downloaded from the server. Known limitation: no historical hash tracking — if a file is re-imported, old hashes are lost.

### 3. Header-Based Authentication
KOReader devices authenticate via `x-auth-user` + `x-auth-key` (MD5 hash) headers, matching the KOReader sync protocol. This runs on a separate `SecurityFilterChain` (stateless, no CSRF) alongside the existing JWT and OPDS auth chains.

### 4. PDF Progress Bridge
Web reader PDF progress is mirrored into the KOReader progress stream. Includes timestamp-based conflict detection with a `force` flag for override. Only supports PDF format — EPUB web reader bridge is intentionally deferred (KOReader uses native format).

### 5. Permission Model
KOReader sync requires the `SYNC_KOREADER` permission, separate from Kobo sync. Admins get this permission automatically via migration. Non-admin users must be granted it explicitly.

### 6. Minimal Upstream Surface
All KOReader-specific code lives in dedicated packages (`service/koreader/`, `repository/koreader/`, `config/security/filter/`). Upstream entity modifications are limited to additive column changes — no existing columns or behavior are altered.

## Known Limitations

1. **Hash matching** only checks current/initial hashes, not historical ones — re-imported files may not match
2. **EPUB web reader bridge** is not implemented — only PDF progress syncs between web reader and KOReader
3. **User creation endpoint** (`POST /api/koreader/users/create`) always returns 403 — users must be created through the Grimmory UI
4. **Password storage** uses MD5 to match KOReader protocol requirements — not suitable for general-purpose auth
