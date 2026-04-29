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

## Plugin Endpoint Usage Summary

The GrimmLink MVP plugin currently uses:

- `GET /api/koreader/users/auth`
- `GET /api/koreader/books/by-hash/{bookHash}`
- `GET /api/koreader/syncs/progress/{bookHash}`
- `PUT /api/koreader/syncs/progress`
- `POST /api/v1/reading-sessions`
- `POST /api/v1/reading-sessions/batch`

The plugin does not currently implement:

- rating sync
- highlights / notes sync
- bookmarks sync
- shelf / library sync
- Web Reader bridge
- EPUB CFI conversion
