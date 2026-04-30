# GrimmLink MVP API Reference

This document reflects the GrimmLink MVP plugin/backend contract currently implemented on the fork.

## Authentication Model

- All `/api/koreader/**` endpoints require `x-auth-user` and `x-auth-key`
- `/api/v1/reading-sessions/**` accepts KOReader companion auth and remains compatible with existing authenticated Grimmory sessions
- The plugin should treat `x-auth-key` as a precomputed auth value and send it verbatim
- Missing or invalid KOReader companion credentials currently return `401 Unauthorized`
- The KOReader auth filter uses servlet `sendError(...)` responses for auth failures, so callers should not assume the auth failure body is JSON

## GET `/api/koreader/users/auth`

- Purpose: validate KOReader companion credentials and return basic authorization status
- Auth requirement: required
- Request payload: none
- Expected response shape:

```json
{
  "status": "ok",
  "username": "reader-name",
  "userId": 123,
  "syncEnabled": true,
  "syncWithGrimmoryReader": false
}
```

- GrimmLink usage:
  - used by `Test Connection`
  - used as the primary plugin auth check before sync operations
- Phase status: MVP

## GET `/api/koreader/books/by-hash/{bookHash}`

- Purpose: find a Grimmory book record using the KOReader-provided content hash
- Auth requirement: required
- Request payload: none
- Path params:
  - `bookHash`: KOReader-visible file/content hash
- Expected response shape:
  - the backend returns the Grimmory `Book` DTO directly
  - GrimmLink currently relies on `id`, `title`, and other descriptive metadata when present
- Not-found behavior:
  - `404 Not Found`
- Plugin note:
  - GrimmLink caches successful hash matches locally
- Phase status: MVP

## GET `/api/koreader/syncs/progress/{bookHash}`

- Purpose: fetch the latest server-side KOReader-native progress state for a matched book
- Auth requirement: required
- Request payload: none
- Path params:
  - `bookHash`: KOReader-visible file/content hash
- Expected response shape:

```json
{
  "timestamp": 1777425600,
  "document": "BOOK_HASH",
  "bookHash": "BOOK_HASH",
  "bookId": 456,
  "fileFormat": "EPUB",
  "progress": "raw-koreader-progress",
  "location": "raw-koreader-location",
  "percentage": 41.2,
  "currentPage": 101,
  "totalPages": 245,
  "device": "KOReader",
  "device_id": "android-tablet-01"
}
```

- Empty-progress behavior:
  - returns `200 OK` with a book-scoped empty progress body when the book is known but no KOReader progress has been stored yet
- Phase status: MVP

## PUT `/api/koreader/syncs/progress`

- Purpose: push KOReader-native reading progress to Grimmory
- Auth requirement: required
- Expected request payload:

```json
{
  "document": "BOOK_HASH",
  "bookHash": "BOOK_HASH",
  "fileFormat": "EPUB",
  "progress": "raw-koreader-progress",
  "location": "raw-koreader-location",
  "percentage": 41.2,
  "currentPage": 101,
  "totalPages": 245,
  "device": "KOReader",
  "deviceId": "android-tablet-01",
  "timestamp": 1777425600
}
```

- Expected response shape:

```json
{
  "status": "progress updated"
}
```

- Notes:
  - backend normalizes `percentage` to `0..100`
  - values in the `0..1` range are accepted and converted
  - KOReader-native progress only
  - no Web Reader bridge
  - no EPUB CFI conversion
- Phase status: MVP

## POST `/api/v1/reading-sessions`

- Purpose: upload a single reading-session event from KOReader
- Auth requirement: required
- Expected request payload:

```json
{
  "bookId": 456,
  "bookType": "EPUB",
  "startTime": "2026-04-29T11:00:00Z",
  "endTime": "2026-04-29T11:15:00Z",
  "durationSeconds": 900,
  "startProgress": 40.0,
  "endProgress": 41.2,
  "progressDelta": 1.2,
  "startLocation": "start-token",
  "endLocation": "end-token",
  "device": "KOReader",
  "deviceId": "android-tablet-01"
}
```

- Expected response shape:
- Expected response shape:
  - `202 Accepted`
  - empty body
- Plugin note:
  - GrimmLink uses single-session upload for one-item flushes
  - the current backend requires `bookId`; the plugin resolves/caches a Grimmory match before uploading sessions
- Phase status: MVP

## POST `/api/v1/reading-sessions/batch`

- Purpose: upload offline-queued reading sessions in batch
- Auth requirement: required
- Expected request payload:

```json
{
  "bookId": 456,
  "bookType": "EPUB",
  "sessions": [
    {
      "startTime": "2026-04-29T11:00:00Z",
      "endTime": "2026-04-29T11:15:00Z",
      "durationSeconds": 900,
      "startProgress": 40.0,
      "endProgress": 41.2,
      "progressDelta": 1.2,
      "startLocation": "start-token",
      "endLocation": "end-token"
    }
  ]
}
```

- Expected response shape:

```json
{
  "totalRequested": 1,
  "successCount": 1,
  "results": [
    {
      "sessionId": 789,
      "startTime": "2026-04-29T11:00:00Z",
      "endTime": "2026-04-29T11:15:00Z"
    }
  ]
}
```

- Phase status: MVP

## GET `/api/v1/reading-sessions/book/{bookId}`

- Purpose: fetch a paginated session history for diagnostics, analytics, and future sync tooling
- Auth requirement: required
- Request payload: none
- Path params:
  - `bookId`: Grimmory book ID
- Expected response shape:

```json
{
  "content": [
    {
      "sessionId": 789,
      "startTime": "2026-04-29T11:00:00Z",
      "endTime": "2026-04-29T11:15:00Z",
      "durationSeconds": 900,
      "startProgress": 40.0,
      "endProgress": 41.2,
      "device": "KOReader",
      "deviceId": "android-tablet-01"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

- Phase status: MVP
- Plugin note:
  - the GrimmLink MVP plugin does not currently call this endpoint

## GET `/api/koreader/shelves`

- Purpose: list shelves available to the authenticated KOReader user (personal shelves and public shelves)
- Auth requirement: required
- Request payload: none
- Expected response shape:

```json
[
  {
    "id": 1,
    "name": "Reading",
    "type": "PERSONAL",
    "bookCount": 3
  },
  {
    "id": 2,
    "name": "Community Picks",
    "type": "PUBLIC",
    "bookCount": 12
  }
]
```

- Notes:
  - `type` is `"PERSONAL"` for shelves owned by the user, `"PUBLIC"` for public shelves
  - returns an empty array if the user has no shelves
- Phase status: Prompt 5 — Shelf Sync

## GET `/api/koreader/shelves/{shelfId}/books`

- Purpose: list books in a specific shelf accessible to the authenticated KOReader user
- Auth requirement: required
- Request payload: none
- Path params:
  - `shelfId`: Grimmory shelf ID
- Expected response shape:

```json
[
  {
    "bookId": 42,
    "title": "Dune",
    "author": "Frank Herbert",
    "fileName": "Dune.epub",
    "fileFormat": "EPUB",
    "fileSizeKb": 512,
    "bookHash": "abc123def456"
  }
]
```

- Field notes:
  - `bookHash` is `currentHash` falling back to `initialHash` from the primary `BookFileEntity`; may be null if the file has not been hashed yet
  - `author` joins all author names with `, `; may be null
  - `fileSizeKb` may be null for un-analyzed files
  - only books the user has library access to are returned; shelf may show fewer books than `bookCount` if access is partial
- Error behavior:
  - `404 Not Found` if shelf does not exist
  - `403 Forbidden` if user does not own the shelf and it is not public
- Phase status: Prompt 5 — Shelf Sync

## GET `/api/koreader/books/{bookId}/download`

- Purpose: download the primary book file for the authenticated KOReader user
- Auth requirement: required
- Request payload: none
- Path params:
  - `bookId`: Grimmory book ID
- Response:
  - `200 OK` with `Content-Type: application/octet-stream`
  - `Content-Disposition: attachment; filename="<originalFilename>"`
  - `Content-Length` header set to file size in bytes
  - response body is the raw book file binary
- Error behavior:
  - `403 Forbidden` if user does not have library access to the book
  - `404 Not Found` if the book does not exist
  - `500` if the file is missing from disk or cannot be read
- Security notes:
  - path traversal is prevented by `FileUtils.requirePathWithinBase()` inside `BookDownloadService`
  - user A cannot download user B's books
- Phase status: Prompt 5 — Shelf Sync

## POST `/api/koreader/shelves/{shelfId}/books/{bookId}/remove`

- Purpose: remove a book from a shelf without deleting the book record or server-side file
- Auth requirement: required
- Request payload: none
- Path params:
  - `shelfId`: Grimmory shelf ID
  - `bookId`: Grimmory book ID
- Expected response shape:

```json
{
  "shelfId": 1,
  "bookId": 42,
  "removedFromShelf": true,
  "deletedFromLibrary": false
}
```

- Notes:
  - the endpoint removes only the shelf membership join row
  - it never deletes the Grimmory book record
  - it never deletes the Grimmory server-side file
  - public shelf visibility does not grant write access; membership removal requires the shelf owner or an admin
  - it is intended for KOReader shelf delete sync only
- Error behavior:
  - `403 Forbidden` if user cannot access the shelf or book
  - `404 Not Found` if shelf or book does not exist
- Phase status: Prompt 5 â€” Shelf Sync

## Annotation / Bookmark / Rating Sync (Prompt 6)

These endpoints back the GrimmLink companion's rating, highlight, note and
bookmark sync. They are KOReader-native: raw KOReader location strings
(xpointer / page) are preserved as-is and are never converted to EPUB CFI.
The Web Reader's existing CFI-based `annotations` / `book_marks` tables are
NOT touched by these endpoints.

None of these endpoints delete book records or library files.

### `GET /api/koreader/books/{bookId}/annotations`
- Returns: `200 OK` with `KoreaderAnnotationDto[]`
- Errors: `403 Forbidden`, `404 Book not found`
- Each item includes: `id`, `dedupeKey`, `koreaderPos`, `page`, `chapter`,
  `text`, `note`, `color`, `drawer`, `source`, `koreaderCreatedAt`,
  `koreaderUpdatedAt`.

### `POST /api/koreader/books/{bookId}/annotations/batch`
- Body: `KoreaderAnnotationDto[]` — each item must include `dedupeKey`.
- Returns: `200 OK` with `KoreaderBatchResultDto`
  (`received`, `inserted`, `updated`, `skipped`, `failed`, `errors[]`).
- Behavior: upsert by `(user_id, book_id, dedupe_key)`. If the incoming
  `koreaderUpdatedAt` is older than the stored value, the row is skipped
  (server-newer-wins).

### `GET /api/koreader/books/{bookId}/bookmarks`
- Returns: `200 OK` with `KoreaderBookmarkDto[]`
- Errors: `403`, `404`
- Each item includes: `id`, `dedupeKey`, `koreaderPos`, `page`, `chapter`,
  `text`, `note`, `source`, `koreaderCreatedAt`.

### `POST /api/koreader/books/{bookId}/bookmarks/batch`
- Body: `KoreaderBookmarkDto[]` — each item must include `dedupeKey`.
- Returns: `200 OK` with `KoreaderBatchResultDto`.
- Behavior: upsert by `(user_id, book_id, dedupe_key)`.

### `GET /api/koreader/books/{bookId}/rating`
- Returns: `200 OK` with `{ "bookId": <id>, "rating": <1..10|null> }`.
- Backed by `user_book_progress.personal_rating`.

### `PUT /api/koreader/books/{bookId}/rating`
- Body: `{ "rating": <1..10|null> }` (null clears the rating)
- Returns: `200 OK` with the new rating.
- Errors: `400` if rating is outside `1..10` and not null.

### Safety policy
- Never deletes any `BookEntity` or `BookFileEntity`.
- Never deletes the existing CFI-based `AnnotationEntity` / `BookMarkEntity`
  rows used by the Web Reader.
- Web Reader bridge and EPUB CFI conversion are **out of scope** for
  Prompt 6 and remain unimplemented.

## Plugin Endpoint Usage Summary

The GrimmLink plugin currently uses:

- `GET /api/koreader/users/auth`
- `GET /api/koreader/books/by-hash/{bookHash}`
- `GET /api/koreader/syncs/progress/{bookHash}`
- `PUT /api/koreader/syncs/progress`
- `POST /api/v1/reading-sessions`
- `POST /api/v1/reading-sessions/batch`
- `GET /api/koreader/shelves` (Shelf Sync)
- `GET /api/koreader/shelves/{shelfId}/books` (Shelf Sync)
- `GET /api/koreader/books/{bookId}/download` (Shelf Sync)
- `POST /api/koreader/shelves/{shelfId}/books/{bookId}/remove` (Shelf Sync)
- `GET /api/koreader/books/{bookId}/annotations` (Annotation Sync)
- `POST /api/koreader/books/{bookId}/annotations/batch` (Annotation Sync)
- `GET /api/koreader/books/{bookId}/bookmarks` (Annotation Sync)
- `POST /api/koreader/books/{bookId}/bookmarks/batch` (Annotation Sync)
- `GET /api/koreader/books/{bookId}/rating` (Annotation Sync)
- `PUT /api/koreader/books/{bookId}/rating` (Annotation Sync)

The plugin does not currently implement:

- Web Reader bridge
- EPUB CFI conversion
- Hardcover rating sync
